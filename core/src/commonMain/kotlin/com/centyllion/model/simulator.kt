package com.centyllion.model

import com.centyllion.expression.parseExpression

import com.centyllion.expression.Expression
import com.centyllion.expression.v
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

const val minField = 1e-15f
const val minFieldLevel = 1e-14f

val emptyFloatArray = FloatArray(0)

class Agent(val index: Int, val direction: Direction, val reactiveId: Int, val age: Int, val fields: FloatArray)

class ApplicableBehavior(
    /** Index in the data where to apply the behavior */
    val index: Int, val age: Int,
    /** The behaviour to apply */
    val behaviour: Behaviour,
    /** List of used neighbours  */
    val usedNeighbours: List<Agent>
) {

    fun ageForSource(reactive: Int) = when {
        reactive == 0 -> age
        reactive > 0 -> usedNeighbours[reactive - 1].age
        else -> 0
    }

    fun apply(simulator: Simulator) {
        // applies main reaction
        simulator.transform(index, index, behaviour.mainProductId, ageForSource(behaviour.sourceReactive))

        // applies other reaction find each neighbour for each reaction
        val reactives = usedNeighbours.sortedBy { it.reactiveId }
        val reactions = behaviour.reaction.sortedBy { it.reactiveId }

        // applies reactions
        reactions.zip(reactives).forEach { (reaction, reactive) ->
            val newAge = ageForSource(reaction.sourceReactive)
            simulator.transform(reactive.index, reactive.index, reaction.productId, newAge)
        }

        // applies actions (agent production/destruction/movement and field modifications)
        // ages for age(n), 1-based "reactive" numbering (same as ageForSource):
        // index 0 = main agent, index k+1 = neighbour of reaction k
        simulator.applyActions(behaviour, index, listOf(age) + usedNeighbours.map { it.age })
    }
}

class Simulator(
    val model: GrainModel,
    val simulation: Simulation
) {
    /** Deterministic engine: every cell owns a stream derived from [Simulation.seed]. */
    val random = SimulationRandom(simulation.seed, simulation.dataSize)

    val initialAgents: IntArray = IntArray(simulation.agents.size) { simulation.agents[it] }
    var agents: IntArray = initialAgents.copyOf()
        private set
    var ages: IntArray = IntArray(initialAgents.size) { if (initialAgents[it] >= 0) 0 else -1 }
        private set

    /** Double-buffering for parallel updates: behaviours are computed from the
     *  state at the start of the step and written into these copies. */
    private var nextAgents: IntArray = agents.copyOf()
    private var nextAges: IntArray = ages.copyOf()

    /** True while [oneStep] is running, so reads use the incremented ages. */
    private var inStep: Boolean = false

    private val parallelUpdate: Boolean get() = !model.isSequentialUpdate

    /** Ages used for reading during a step (incremented copy in parallel mode). */
    private val readAges: IntArray get() = if (parallelUpdate && inStep) nextAges else ages

    val currentAgents get() = if (step == 0) initialAgents else agents

    val fieldMaxId = model.fields.maxOfOrNull { it.id } ?: 0

    val fields get() = if (currentFields) fields1 else fields2

    val nextFields get() = if (!currentFields) fields1 else fields2

    fun field(id: Int) = fields[id] ?: emptyFloatArray

    private var currentFields: Boolean = true

    private val fields1: Map<Int, FloatArray> =
        model.fields.associate { it.id to FloatArray(simulation.agents.size) { minField } }

    private val fields2: Map<Int, FloatArray> =
        model.fields.associate { it.id to FloatArray(simulation.agents.size) { minField } }

    private val expressionEvaluator = createEvaluator(this)

    private val evaluators: Map<Int, FieldEvaluator> = createFieldEvaluators(expressionEvaluator)

    /** True when at least one field formula uses the count() function. */
    private val fieldsRequireCount: Boolean =
        evaluators.values.filterIsInstance<FormulaFieldEvaluator>().any { it.usesCount }

    /** Per-thread evaluation context. Evaluators are stateful (position, step, current),
     *  so each parallel worker needs its own. */
    private inner class ThreadContext(
        val evaluator: NetbioDynEvaluator,
        val evaluators: Map<Int, FieldEvaluator>
    ) {
        /** Cell description reused by every cell of this thread. */
        val cell: CellContext = CellContext(this@Simulator)
    }

    private val mainContext = ThreadContext(expressionEvaluator, evaluators)

    private val threadContexts: MutableList<ThreadContext> = mutableListOf()

    /** Set by the pause() meta-action: the run loop stops after the current step. */
    var pauseRequested: Boolean = false
        private set

    /** Clears the pause request once the run loop has stopped. */
    fun clearPauseRequest() {
        pauseRequested = false
    }

    /**
     * Grid commands (selection, clipboard, agent visibility) queued by the meta-actions during
     * the last step. The core does not know the view, which owns the selection and the
     * clipboard: the view drains this list at the end of the step and applies the commands in
     * order, with the same code as the manual edition.
     */
    private val gridCommands = mutableListOf<GridCommand>()

    /** Pending grid commands, in the order the formula asked for them (read-only view). */
    val pendingGridCommands: List<GridCommand> get() = gridCommands

    /** Returns the pending grid commands and empties the queue: called once per step by the view. */
    fun drainGridCommands(): List<GridCommand> {
        if (gridCommands.isEmpty()) return emptyList()
        val drained = gridCommands.toList()
        gridCommands.clear()
        return drained
    }

    /** Activation state at run time, initialised from the model and changed by Activate/Inactivate. */
    private val runtimeEnabled: BooleanArray by lazy { BooleanArray(allBehaviours.size) { allBehaviours[it].enabled } }

    /** Activation state at the start of the run, restored by [reset] (rewind). */
    private var initialEnabled: BooleanArray? = null

    /** Snapshot of the activation taken before the very first step. */
    private fun activationSnapshot(): BooleanArray =
        initialEnabled ?: runtimeEnabled.copyOf().also { initialEnabled = it }

    /** Run time activation of the simulation modifiers, changed by Use(id) / Unuse(id). */
    private val runtimeModifierEnabled: BooleanArray by lazy {
        BooleanArray(model.modifiers.size) { model.modifiers[it].enabled }
    }

    private var initialModifierEnabled: BooleanArray? = null

    private fun modifierSnapshot(): BooleanArray =
        initialModifierEnabled ?: runtimeModifierEnabled.copyOf().also { initialModifierEnabled = it }

    /** Run time activation of the modifier with that id (true when the id is unknown). */
    fun isModifierUsed(id: Int): Boolean {
        val index = model.modifiers.indexOfFirst { it.id == id }
        return index < 0 || runtimeModifierEnabled[index]
    }

    /** Behaviours that already executed their meta-actions during the current step. */
    private val metaActionsDone = HashSet<Behaviour>()

    /** True when a simulation modifier changed the state during the last step. */
    var modifiersChanged: Boolean = false
        private set

    /** True when Activate/Inactivate changed the runtime state since the last check. */
    var activationChanged: Boolean = false
        private set

    fun clearActivationChanged() {
        activationChanged = false
    }

    /** Runtime activation of the behaviour with that id (true when the id is unknown). */
    fun isRuntimeEnabled(id: Int): Boolean {
        val index = behaviourIndexForId(id)
        return index < 0 || runtimeEnabled[index]
    }

    private fun behaviourIndexForId(id: Int): Int = allBehaviours.indexOfFirst { it.id == id }

    /** True for the actions that act on the simulation as a whole rather than on a cell. */
    private fun isMetaAction(action: Action): Boolean = when (action) {
        Action.Pause, is Action.AddMany, is Action.AddRect, is Action.AddDisc,
        is Action.EraseRectAll, is Action.EraseDiscAll, is Action.EraseRect, is Action.EraseDisc,
        is Action.EraseAll, is Action.Activate, is Action.Inactivate -> true
        else -> false
    }

    /** Draws a line of agents from (x1, y1) to (x2, y2), on the empty cells only. */
    private fun drawLine(grainId: Int, x1: Int, y1: Int, x2: Int, y2: Int) {
        val startX = x1.coerceIn(0, simulation.width - 1)
        val startY = y1.coerceIn(0, simulation.height - 1)
        val endX = x2.coerceIn(0, simulation.width - 1)
        val endY = y2.coerceIn(0, simulation.height - 1)
        val deltaX = if (endX > startX) endX - startX else startX - endX
        val deltaY = if (endY > startY) endY - startY else startY - endY
        val negativeDeltaY = -deltaY
        val stepX = if (startX < endX) 1 else -1
        val stepY = if (startY < endY) 1 else -1
        var x = startX
        var y = startY
        var error = deltaX + negativeDeltaY
        while (true) {
            val slot = y * simulation.width + x
            if (agents[slot] < 0) {
                agents[slot] = grainId
                ages[slot] = 0
            }
            if (x == endX && y == endY) break
            val doubled = 2 * error
            if (doubled >= negativeDeltaY) {
                error += negativeDeltaY
                x += stepX
            }
            if (doubled <= deltaX) {
                error += deltaX
                y += stepY
            }
        }
    }

    /** Calls [block] with the index of every cell of the rectangle, clamped to the grid. */
    private fun forEachCellInRect(x1: Int, y1: Int, x2: Int, y2: Int, block: (Int) -> Unit) {
        val left = minOf(x1, x2).coerceIn(0, simulation.width - 1)
        val right = maxOf(x1, x2).coerceIn(0, simulation.width - 1)
        val top = minOf(y1, y2).coerceIn(0, simulation.height - 1)
        val bottom = maxOf(y1, y2).coerceIn(0, simulation.height - 1)
        for (y in top..bottom) for (x in left..right) block(y * simulation.width + x)
    }

    /** Calls [block] with the index of every cell of the disc, clamped to the grid. */
    private fun forEachCellInDisc(centerX: Int, centerY: Int, radius: Int, block: (Int) -> Unit) {
        val r = maxOf(0, radius)
        forEachCellInRect(centerX - r, centerY - r, centerX + r, centerY + r) { index ->
            val dx = index % simulation.width - centerX
            val dy = index / simulation.width - centerY
            if (dx * dx + dy * dy <= r * r) block(index)
        }
    }

    /** Collector reused by the sequential path (no map, no per-step allocation). */
    private val sequentialCollector = FastCollector(simulation.dataSize)

    private var parallelExecutor: ParallelExecutor? = null
    private var parallelExecutorThreads: Int = 0

    private fun createFieldEvaluators(evaluator: NetbioDynEvaluator): Map<Int, FieldEvaluator> =
        model.fields.associate { field ->
            val formula = field.formula
            field.id to when {
                formula.isBlank() -> SimpleFieldEvaluator
                formula.trim() == "current" -> SimpleFieldEvaluator
                validateFormula(formula) != null -> SimpleFieldEvaluator
                else -> FormulaFieldEvaluator(field, formula, evaluator)
            }
        }

    private fun ensureThreadContexts(count: Int) {
        while (threadContexts.size < count) {
            val evaluator = createEvaluator(this)
            threadContexts.add(ThreadContext(evaluator, createFieldEvaluators(evaluator)))
        }
    }

    private fun executorFor(threads: Int): ParallelExecutor {
        val current = parallelExecutor
        if (current == null || parallelExecutorThreads != threads) {
            current?.close()
            val created = ParallelExecutor(threads)
            parallelExecutor = created
            parallelExecutorThreads = threads
            return created
        }
        return current
    }

    val grainCountHistory: Map<Grain, MutableList<Int>> = grainsCounts().let { counts ->
        model.grains.associateWith { mutableListOf(counts[it.id] ?: 0) }
    }

    val fieldAmountHistory: Map<Field, MutableList<Float>> = fieldAmounts().let { amounts ->
        model.fields.associateWith { mutableListOf(amounts[it.id] ?: 0f) }
    }

    var step = 0

    private val allBehaviours = model.behaviours + model.grains.mapNotNull { it.moveBehaviour() }

    private val speeds = allBehaviours.associateWith { it.probability }.toMutableMap()

    private val reactiveGrains = allBehaviours.mapNotNull { model.grainForId(it.mainReactiveId) }.toSet()

    // Caches an array of Grain placed with their id for fast grain resolution
    private val grainIdArray = Array((model.grains.maxOfOrNull { it.id } ?: -1) + 1)
        { id -> model.grains.find { it.id == id } }

    fun grainForId(id: Int): Grain? = if (id < 0) null else grainIdArray[id]

    // Flat slots for the per-step counters: grain and field ids are not necessarily
    // contiguous, and hashing a data class per cell was a major cost.
    private val grainSlot: IntArray = IntArray((model.grains.maxOfOrNull { it.id } ?: -1) + 1) { -1 }
    private val fieldSlot: IntArray = IntArray(fieldMaxId + 1) { -1 }

    /** Speeds indexed like [allBehaviours], to avoid a hash lookup per cell. */
    private val speedOf: DoubleArray = DoubleArray(allBehaviours.size) { allBehaviours[it].probability }

    /** Behaviour indexes per agent type: slot 0 = empty cells (mainReactiveId == -1),
     *  slot `grainId + 1` = behaviours reacting on that grain. */
    /** Neighbour table: index of the eight neighbours of each cell, computed once.
     *  Directions follow [CellContext.DIRECTIONS]; wrapping is applied here, the
     *  non-toric walls are still checked with `crossesBorder` where they matter. */
    private val neighbourIndex: IntArray = IntArray(simulation.dataSize * CellContext.DIRECTIONS.size)

    private val behaviourIndexes: Array<IntArray> =
        Array((model.grains.maxOfOrNull { it.id } ?: -1) + 2) { slot ->
            val grainId = slot - 1
            allBehaviours.indices.filter { allBehaviours[it].mainReactiveId == grainId }.toIntArray()
        }

    init {
        model.grains.forEachIndexed { slot, grain -> grainSlot[grain.id] = slot }
        model.fields.forEachIndexed { slot, field -> fieldSlot[field.id] = slot }
        for (i in 0 until simulation.dataSize) {
            val base = i * CellContext.DIRECTIONS.size
            for (k in CellContext.DIRECTIONS.indices) {
                neighbourIndex[base + k] = simulation.moveIndex(i, CellContext.DIRECTIONS[k])
            }
        }
    }

    fun grainAtIndex(index: Int): Grain? {
        val id = idAtIndex(index)
        return if (id < 0) null else grainIdArray[id]
    }

    fun lastGrainsCount(): Map<Grain, Int> =
        grainCountHistory.map { it.key to it.value.last() }.toMap()

    fun lastFieldAmount(): Map<Field, Float> =
        fieldAmountHistory.map { it.key to it.value.last() }.toMap()

    fun getSpeed(behaviour: Behaviour) = speeds[behaviour] ?: 0.0

    fun setSpeed(behaviour: Behaviour, speed: Double) {
        speeds[behaviour] = speed
        val index = allBehaviours.indexOf(behaviour)
        if (index >= 0) speedOf[index] = speed
    }

    /** Executes one step and returns the applied behaviours and the dead indexes. */
    fun oneStep(): Pair<Collection<ApplicableBehavior>, Collection<Int>> {
        // the rewind state must be captured before any Activate/Inactivate/Use runs
        activationSnapshot()
        modifierSnapshot()
        // In parallel mode, prepare the next-state buffers and pre-increment ages
        // so that all behaviours are computed from the state at the start of the step.
        if (parallelUpdate) {
            agents.copyInto(nextAgents)
            ages.copyInto(nextAges)
            for (i in nextAgents.indices) {
                if (nextAgents[i] >= 0) nextAges[i] += 1
            }
            inStep = true
        }

        try {
            return oneStepInternal()
        } finally {
            if (parallelUpdate) {
                inStep = false
            }
        }
    }

    private fun oneStepInternal(): Pair<Collection<ApplicableBehavior>, Collection<Int>> {
        // a meta-action runs once per behaviour and per step, not once per cell
        metaActionsDone.clear()

        // applies agents dying process (flat counters, indexed by slot)
        val currentCount = IntArray(model.grains.size)
        val currentFieldAmounts = FloatArray(model.fields.size)

        // fields indexed by slot, to avoid a map lookup per cell
        val fieldArrays = Array(model.fields.size) { fields[model.fields[it].id] }
        val nextFieldArrays = Array(model.fields.size) { nextFields[model.fields[it].id] }

        val dead = mutableListOf<Int>()

        // all will contain index of agents as keys associated to a list of applicable behaviors
        // if an agent doesn't contain any applicable behavior it will have an empty list.
        // if an agent can't move and doesn't contain any applicable behavior, it won't be present in the map
        // filters behaviors that are concurrent to preserve the matter
        val toExecute: Collection<ApplicableBehavior> = if (shouldParallelize()) {
            val all = mutableMapOf<Int, MutableList<ApplicableBehavior>>()
            runParallelStep(all, currentCount, currentFieldAmounts, dead, fieldArrays, nextFieldArrays)
            resolveConflicts(all, random)
        } else {
            val context = mainContext
            sequentialCollector.reset()
            for (i in 0 until simulation.dataSize) {
                processCell(
                    i, context, sequentialCollector, currentCount, currentFieldAmounts, dead,
                    random, fieldArrays, nextFieldArrays
                )
            }
            sequentialCollector.resolve(random)
        }
        toExecute.forEach { it.apply(this) }

        // stores count for each grain
        for (slot in currentCount.indices) {
            val grain = model.grains[slot]
            val value = currentCount[slot]
            grainCountHistory[grain]?.add(value)
            maxGrainCount[grain.id] = max(maxGrainCount[grain.id] ?: 0, value)
        }

        // stores field amounts
        for (slot in currentFieldAmounts.indices) {
            val field = model.fields[slot]
            val value = currentFieldAmounts[slot]
            fieldAmountHistory[field]?.add(value)
            maxFieldAmount[field.id] = max(maxFieldAmount[field.id] ?: 0f, value)
        }

        // swap fields
        currentFields = !currentFields

        // In parallel mode, swap the agent/age buffers so the next state becomes current
        if (parallelUpdate) {
            val tmpAgents = agents
            agents = nextAgents
            nextAgents = tmpAgents
            val tmpAges = ages
            ages = nextAges
            nextAges = tmpAges
        }

        // the modifiers act once per step, after the behaviours, on the state of the step
        // that has just been computed (same step number as the behaviours saw)
        modifiersChanged = false
        // a command belongs to one step: a view that did not drain the previous queue (a UI
        // without grid edition, for instance) must not accumulate it forever
        gridCommands.clear()
        applyModifiers()

        // count a step
        step += 1

        return toExecute to dead
    }

    /** True when the per-cell work should be executed on multiple threads.
     *  Requires the parallel update mode (double buffering) and a large enough grid. */
    private fun shouldParallelize(): Boolean =
        parallelUpdate && simulation.dataSize >= 10_000

    /** Result of processing one block of cells. */
    private data class BlockResult(
        val all: Map<Int, List<ApplicableBehavior>>,
        val count: IntArray,
        val fieldAmounts: FloatArray,
        val dead: List<Int>
    )

    /** Runs the per-cell work on multiple threads (parallel update mode, large grids).
     *  Each block builds its own accumulators; results are merged afterwards. */
    private fun runParallelStep(
        all: MutableMap<Int, MutableList<ApplicableBehavior>>,
        currentCount: IntArray,
        currentFieldAmounts: FloatArray,
        dead: MutableList<Int>,
        fields: Array<FloatArray?>,
        nextFields: Array<FloatArray?>
    ) {
        val blocks = splitIntoBlocks(simulation.dataSize, recommendedThreads())
        ensureThreadContexts(blocks.size)
        val executor = executorFor(blocks.size)

        val results = executor.mapBlocks(blocks) { block ->
            val context = threadContexts[block.index]
            val localAll = mutableMapOf<Int, MutableList<ApplicableBehavior>>()
            val localCount = IntArray(model.grains.size)
            val localFieldAmounts = FloatArray(model.fields.size)
            val localDead = mutableListOf<Int>()
            // cells are disjoint between blocks: each cell draws from its own stream, so the
            // trajectory does not depend on how the grid is split across threads
            val localCollector = MapCollector(localAll)
            for (i in block.range) {
                processCell(i, context, localCollector, localCount, localFieldAmounts, localDead, random, fields, nextFields)
            }
            BlockResult(localAll, localCount, localFieldAmounts, localDead)
        }

        // merge per-block results
        results.forEach { r ->
            r.all.forEach { (index, behaviours) ->
                all.getOrPut(index) { mutableListOf() }.addAll(behaviours)
            }
            for (slot in currentCount.indices) {
                currentCount[slot] += r.count[slot]
            }
            for (slot in currentFieldAmounts.indices) {
                currentFieldAmounts[slot] += r.fieldAmounts[slot]
            }
            dead.addAll(r.dead)
        }
    }

    /**
     * Deterministic resolution of the concurrent behaviours coming from the parallel path:
     * cells are visited in index order, so the trajectory no longer depends on the hash
     * order of a map.
     */
    private fun resolveConflicts(
        all: Map<Int, List<ApplicableBehavior>>,
        random: SimulationRandom
    ): List<ApplicableBehavior> {
        val excluded = HashSet<ApplicableBehavior>()
        for (cellIndex in all.keys.sorted()) {
            val behaviours = all.getValue(cellIndex)
            if (behaviours.size <= 1) continue
            val kept = behaviours[random.nextInt(cellIndex, behaviours.size)]
            for (behaviour in behaviours) if (behaviour !== kept) excluded.add(behaviour)
        }
        val result = LinkedHashSet<ApplicableBehavior>()
        for (behaviours in all.values) for (behaviour in behaviours) {
            if (behaviour !in excluded) result.add(behaviour)
        }
        return result.toList()
    }

    /**
     * Builds the neighbour view of a cell, only when a behaviour was actually selected.
     *
     * When [visibleOnly] is true (a behaviour with a closed wall), the neighbours that the
     * precomputed table reached through a closed wall are left out: they are not instantiated
     * and their fields are not read, so they are simply absent from the view.
     */
    private fun neighboursFor(cell: CellContext, visibleOnly: Boolean = false): List<Pair<Direction, Agent>> {
        val result = ArrayList<Pair<Direction, Agent>>(CellContext.DIRECTIONS.size)
        for (k in CellContext.DIRECTIONS.indices) {
            if (visibleOnly && cell.hiddenDirections[k]) continue
            val direction = CellContext.DIRECTIONS[k]
            val index = cell.neighbourIndices[k]
            val fieldValues = if (model.fields.isEmpty()) emptyFloatArray
            else FloatArray(fieldMaxId + 1) { field(it).getOrNull(index) ?: 0f }
            result.add(direction to Agent(index, direction, cell.neighbourIds[k], cell.neighbourAges[k], fieldValues))
        }
        return result
    }

    /**
     * Reaction phase of a selected behaviour: candidate reactives, combination choice and
     * registration for concurrency resolution. Shared by occupied and empty cells.
     */
    private fun applyBehaviour(
        i: Int,
        cell: CellContext,
        behaviour: Behaviour,
        collector: BehaviourCollector,
        random: SimulationRandom,
        ageOfMainAgent: Int
    ) {
        val position = cell.position
        // a behaviour with a closed wall only sees the neighbours that are not hidden by it
        val visibleOnly = behaviour.hasClosedBoundary && cell.hideNeighboursBehind(behaviour.boundaryConditions) > 0
        val neighbours = neighboursFor(cell, visibleOnly)
        val fieldValues = fieldsAtIndex(i)

        // for each reaction, find all possible reactives
        val possibleReactions = behaviour.reaction
            .map { reaction ->
                val bc = model.grainForId(reaction.productId)?.boundaryConditions
                neighbours.filter { (d, a) ->
                    reaction.reactiveId == a.reactiveId && reaction.allowedDirection.contains(d) &&
                            (reaction.reactiveId >= 0 || bc == null || !bc.crossed(position, d, simulation.width, simulation.height))
                }
            }

        // combines possibilities (read through a view: no list of lists is built)
        val combinations = CombinationView(possibleReactions)

        val chosenCombination = when {
            // optimized case when no field is used for the behaviour
            model.fields.isEmpty() || !behaviour.fieldInfluenced -> {
                random.nextInt(i, combinations.size)
            }

            else -> {
                // computes weight of each combination by adding the influence of all neighbours for a combination
                val influence = FloatArray(combinations.size) { c ->
                    var sum = 0f
                    for (reaction in possibleReactions.indices) {
                        val agent = combinations.get(c, reaction).second
                        behaviour.fieldInfluences.forEach { influence ->
                            val sourceField = fieldValues
                                .find { it.first == influence.key }?.second
                                ?: 0f

                            val delta = log10(agent.fields[influence.key]) - log10(sourceField)
                            sum += delta * influence.value
                        }
                    }
                    sum
                }

                val min = influence.minOrNull() ?: 0f
                var current = 0f
                for (index in influence.indices) {
                    // translates influence to positive float and to the power of 6 for a stronger effect
                    val translated = (influence[index] - min + 1f).pow(6)
                    // computes the total value
                    influence[index] = current + translated
                    current += translated
                }

                // chooses one randomly influenced by the fields
                val probability = random.nextDouble(i) * current
                influence.indexOfFirst { probability < it }
            }
        }
        // registers behaviour for concurrency
        val usedNeighbours = combinations.elements(chosenCombination).map { it.second }
        val behavior = ApplicableBehavior(i, ageOfMainAgent, behaviour, usedNeighbours)
        // adds applicable to current agent, then to the neighbours it uses
        collector.add(i, behavior)
        usedNeighbours.forEach { collector.add(it.index, behavior) }
    }

    /** Processes one cell: death, ageing, behaviour deliberation and field update. */
    private fun processCell(
        i: Int,
        context: ThreadContext,
        collector: BehaviourCollector,
        currentCount: IntArray,
        currentFieldAmounts: FloatArray,
        dead: MutableList<Int>,
        random: SimulationRandom,
        fields: Array<FloatArray?>,
        nextFields: Array<FloatArray?>
    ) {
        val cell = context.cell
        val position = simulation.toPosition(i)
        cell.step = step
        cell.x = position.x
        cell.y = position.y
        cell.z = position.z
        cell.position = position
        cell.evaluator = context.evaluator
        cell.fieldSlot = fieldSlot
        // the field formulas read the evaluator directly (position and step)
        context.evaluator.step = step.v
        context.evaluator.x = position.x.v
        context.evaluator.y = position.y.v
        context.evaluator.z = position.z.v
        if (cell.fieldValues.size != fields.size) cell.fieldValues = FloatArray(fields.size)
        for (slot in fields.indices) {
            cell.fieldValues[slot] = fields[slot]?.get(i) ?: 0f
        }

        // neighbours and their counts, computed once for the whole cell (no allocation)
        val base = i * CellContext.DIRECTIONS.size
        cell.counts.clear()
        var empty = 0
        for (k in CellContext.DIRECTIONS.indices) {
            val index = neighbourIndex[base + k]
            val id = currentAgents[index]
            cell.neighbourIndices[k] = index
            cell.neighbourIds[k] = id
            cell.neighbourAges[k] = readAges[index]
            if (id >= 0) cell.counts[id] = (cell.counts[id] ?: 0) + 1 else empty += 1
        }
        cell.emptyCount = empty
        cell.occupiedCount = CellContext.DIRECTIONS.size - empty
        cell.counts[COUNT_ALL_AGENTS] = cell.occupiedCount
        cell.counts[COUNT_EMPTY_CELLS] = empty

        val grain = grainAtIndex(i)
        cell.grain = grain
        if (grain != null) {
            // does the grain dies ?
            if (grain.halfLife > 0.0 && random.nextDouble(i) <= grain.deathProbability) {
                // it dies, doesn't count
                transform(i, i, null)
                dead.add(i)
            } else {
                currentCount[grainSlot[grain.id]] += 1
                ageGrain(i)
                cell.age = ageAtIndex(i)

                val candidates = behaviourIndexes[grain.id + 1]
                if (candidates.isNotEmpty()) {
                    // searches for applicable behaviours and filters by probability
                    val applicable = ArrayList<Int>(4)
                    for (bi in candidates) {
                        if (!runtimeEnabled[bi]) continue
                        if (allBehaviours[bi].applicable(cell) && random.nextDouble(i) < speedOf[bi]) {
                            applicable.add(bi)
                        }
                    }

                    // selects behaviour if any is applicable, then one at random
                    if (applicable.isNotEmpty()) {
                        val behaviour = allBehaviours[applicable[random.nextInt(i, applicable.size)]]
                        applyBehaviour(i, cell, behaviour, collector, random, readAges[i])
                    }
                }
            }
        } else {
            // empty cell: check for generation behaviours (mainReactiveId == -1)
            // for generation, use the simulation step as the "age" value
            cell.age = step
            val applicable = ArrayList<Int>(2)
            for (bi in behaviourIndexes[0]) {
                if (!runtimeEnabled[bi]) continue
                if (allBehaviours[bi].applicable(cell) && random.nextDouble(i) < speedOf[bi]) {
                    applicable.add(bi)
                }
            }
            if (applicable.isNotEmpty()) {
                val behaviour = allBehaviours[applicable[random.nextInt(i, applicable.size)]]
                applyBehaviour(i, cell, behaviour, collector, random, 0)
            }
        }

        // compute fields values for step
        // count() in field formulas: number of agents in the 8 adjacent cells
        if (fieldsRequireCount) {
            context.evaluator.agentCounts = cell.counts
            context.evaluator.emptyCount = cell.emptyCount
        }
        for (slot in model.fields.indices) {
            val field = model.fields[slot]
            val current = fields[slot]
            val next = nextFields[slot]
            val count = field.allowedDirection.size

            var permeableSum = 0f
            for (allowed in field.allowedDirection) {
                if (!field.toric && crossesBorder(position, allowed, simulation.width, simulation.height)) continue
                val index = simulation.moveIndex(i, allowed)
                permeableSum += (grainAtIndex(index)?.fieldPermeable?.get(field.id) ?: 1f)
            }

            if (next != null && current != null) {
                var diffusionSum = 0f
                for (opposite in field.oppositeDirections) {
                    if (!field.toric && crossesBorder(position, opposite, simulation.width, simulation.height)) continue
                    val index = simulation.moveIndex(i, opposite)
                    val permeable = (grainAtIndex(index)?.fieldPermeable?.get(field.id) ?: 1f)
                    diffusionSum += current[index] * field.speed * permeable
                }

                val level =
                    // current value cut down
                    current[i] * (1.0f - field.speed * permeableSum / count) +
                            // adding or removing from current agent if any
                            (grain?.fieldProductions?.get(field.id) ?: 0f) +
                            // diffusion from agent around using opposite directions
                            diffusionSum / count

                val diffused = level * (1f - field.deathProbability)
                val evaluated = context.evaluators[field.id]
                    ?.evaluateField(diffused)
                    ?: diffused

                next[i] = evaluated.coerceIn(minField, 1f)

                // count current field amounts
                currentFieldAmounts[slot] += next[i]
            }
        }
    }

    fun reset() {
        // rewind: the activation state comes back to what it was at the start of the run
        activationSnapshot().copyInto(runtimeEnabled)
        modifierSnapshot().copyInto(runtimeModifierEnabled)
        activationChanged = true
        pauseRequested = false
        // commands queued by a step that is now rewound are meaningless
        gridCommands.clear()
        initialAgents.copyInto(agents)
        for (i in ages.indices) {
            ages[i] = if (agents[i] != -1) 0 else -1
            fields.forEach { it.value[i] = minField }
        }
        // Also reset the parallel buffers so they don't hold stale state
        agents.copyInto(nextAgents)
        ages.copyInto(nextAges)

        step = 0
        // rewind the streams: running again after a reset repeats the same trajectory
        random.reset()
        resetCount()
    }

    /**
     * Test/initialisation support: set the initial value of field [fieldId]
     * at [index]. Must be called before the first step (the current field
     * buffer is filled with [minField] at construction and by [reset]).
     * Values are clamped to [minField]..1f, like every field update.
     */
    fun setInitialField(fieldId: Int, index: Int, value: Float) {
        val f = fields[fieldId] ?: error("setInitialField: unknown field id $fieldId")
        check(index in 0 until f.size) { "setInitialField: index $index out of range 0..${f.size - 1}" }
        f[index] = value.coerceIn(minField, 1f)
    }

    /** Releases the parallel executor. Call when the simulator is no longer used. */
    fun close() {
        parallelExecutor?.close()
        parallelExecutor = null
        threadContexts.clear()
    }

    fun resetCount() {
        val counts = grainsCounts()
        grainCountHistory.forEach {
            it.value.clear()
            it.value.add(counts.getOrElse(it.key.id) { 0 })
        }

        maxGrainCount.clear()
        grainsCounts().forEach { (k, v) -> maxGrainCount[k] = v }

        fieldAmountHistory.forEach {
            it.value.clear()
            it.value.add(0.0f)
        }
        maxFieldAmount.clear()
        maxFieldAmount.forEach { (k, v) -> maxFieldAmount[k] = 0f }
    }

    fun idAtIndex(index: Int) = currentAgents[index]

    fun ageAtIndex(index: Int): Int = readAges[index]

    fun ageGrain(index: Int) {
        // In parallel mode, ages are pre-incremented at the start of the step.
        if (!parallelUpdate) {
            ages[index] += 1
        }
    }

    fun fieldsAtIndex(index: Int) = fields.map { it.key to it.value[index] }

    fun transform(sourceIndex: Int, targetIndex: Int, newId: Int?, newAge: Int = -1) {
        val targetAgents = if (parallelUpdate) nextAgents else agents
        val targetAges = if (parallelUpdate) nextAges else ages
        targetAgents[sourceIndex] = -1
        targetAges[sourceIndex] = -1
        targetAgents[targetIndex] = newId ?: -1
        targetAges[targetIndex] = when {
            newId != null -> newAge
            else -> -1
        }
    }

    /** Parsed conditions and actions of the simulation modifiers. */
    private val parsedModifierConditions = mutableMapOf<SimulationModifier, com.centyllion.expression.Expression>()
    private val parsedModifierActions = mutableMapOf<SimulationModifier, List<Action>>()

    /**
     * Applies the simulation modifiers: each one is evaluated **once per step**, outside any
     * cell, and executes its meta-actions when its condition holds.
     */
    private fun applyModifiers() {
        if (model.modifiers.isEmpty()) return
        val evaluator = mainContext.evaluator
        evaluator.absoluteCoordinates = true
        evaluator.step = step.v
        try {
            for (modifierIndex in model.modifiers.indices) {
                val modifier = model.modifiers[modifierIndex]
                // Use(id) / Unuse(id) drive this state; the model gives the initial value
                if (!runtimeModifierEnabled[modifierIndex]) continue
                // probability drawn once per step, like the behaviour probabilities
                if (modifier.probability < 1.0 && random.nextDouble(0) >= modifier.probability) continue
                val condition = modifier.formula
                val holds = if (condition.isBlank()) true else runCatching {
                    val expression = parsedModifierConditions.getOrPut(modifier) { condition.parseExpression() }
                    evaluator.evaluate(expression).boolean
                }.getOrDefault(false)
                if (!holds) continue
                val actions = parsedModifierActions.getOrPut(modifier) {
                    runCatching { modifier.actions.parseModifierActions() }.getOrDefault(emptyList())
                }
                executeMetaActions(actions, evaluator)
            }
        } finally {
            evaluator.absoluteCoordinates = false
        }
    }

    /** Executes the meta-actions of a modifier, directly on the current state (no cell). */
    private fun executeMetaActions(actions: List<Action>, evaluator: NetbioDynEvaluator) {
        // the view must redraw even when no behaviour was applied
        if (actions.any {
                it is Action.AddMany || it is Action.AddRect || it is Action.AddDisc ||
                    it is Action.EraseRectAll || it is Action.EraseDiscAll ||
                    it is Action.EraseRect || it is Action.EraseDisc || it is Action.EraseAll ||
                    it is Action.DrawLine ||
                    // a grid command changes the view (selection, clipboard, visibility): the
                    // run loop must then call the view, which drains the queue
                    it is Action.Grid
            }
        ) modifiersChanged = true
        for (action in actions) {
            try {
                when (action) {
                    Action.Pause -> pauseRequested = true

                    is Action.AddMany -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        var remaining = action.count.evalInt(evaluator)
                        var guard = 0
                        while (remaining > 0 && guard < agents.size * 4) {
                            val slot = random.nextInt(0, agents.size)
                            if (agents[slot] < 0) {
                                agents[slot] = id
                                ages[slot] = 0
                                remaining -= 1
                            }
                            guard += 1
                        }
                    }

                    is Action.AddRect -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        val candidates = ArrayList<Int>()
                        forEachCellInRect(
                            action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                            action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                        ) { slot -> if (agents[slot] < 0) candidates.add(slot) }
                        repeat(minOf(action.count.evalInt(evaluator), candidates.size)) {
                            val slot = candidates.removeAt(random.nextInt(0, candidates.size))
                            agents[slot] = id
                            ages[slot] = 0
                        }
                    }

                    is Action.AddDisc -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        val candidates = ArrayList<Int>()
                        forEachCellInDisc(
                            action.x.evalInt(evaluator), action.y.evalInt(evaluator), action.radius.evalInt(evaluator)
                        ) { slot -> if (agents[slot] < 0) candidates.add(slot) }
                        repeat(minOf(action.count.evalInt(evaluator), candidates.size)) {
                            val slot = candidates.removeAt(random.nextInt(0, candidates.size))
                            agents[slot] = id
                            ages[slot] = 0
                        }
                    }

                    is Action.EraseRectAll -> forEachCellInRect(
                        action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                        action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                    ) { slot -> agents[slot] = -1; ages[slot] = -1 }

                    is Action.EraseDiscAll -> forEachCellInDisc(
                        action.x.evalInt(evaluator), action.y.evalInt(evaluator), action.radius.evalInt(evaluator)
                    ) { slot -> agents[slot] = -1; ages[slot] = -1 }

                    is Action.EraseRect -> {
                        val id = action.grain.evalInt(evaluator)
                        forEachCellInRect(
                            action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                            action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                        ) { slot -> if (agents[slot] == id) { agents[slot] = -1; ages[slot] = -1 } }
                    }

                    is Action.EraseDisc -> {
                        val id = action.grain.evalInt(evaluator)
                        forEachCellInDisc(
                            action.x.evalInt(evaluator), action.y.evalInt(evaluator), action.radius.evalInt(evaluator)
                        ) { slot -> if (agents[slot] == id) { agents[slot] = -1; ages[slot] = -1 } }
                    }

                    is Action.DrawLine -> {
                        modifiersChanged = true
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        drawLine(
                            id, action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                            action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                        )
                    }

                    is Action.EraseAll -> {
                        val id = action.grain.evalInt(evaluator)
                        for (slot in agents.indices) if (agents[slot] == id) { agents[slot] = -1; ages[slot] = -1 }
                    }

                    is Action.Activate -> {
                        val index = behaviourIndexForId(action.behaviourId.evalInt(evaluator))
                        if (index >= 0 && !runtimeEnabled[index]) {
                            runtimeEnabled[index] = true
                            activationChanged = true
                        }
                    }

                    is Action.Inactivate -> {
                        val index = behaviourIndexForId(action.behaviourId.evalInt(evaluator))
                        if (index >= 0 && runtimeEnabled[index]) {
                            runtimeEnabled[index] = false
                            activationChanged = true
                        }
                    }

                    is Action.Use -> {
                        val index = model.modifiers.indexOfFirst { it.id == action.modifierId.evalInt(evaluator) }
                        if (index >= 0 && !runtimeModifierEnabled[index]) {
                            runtimeModifierEnabled[index] = true
                            activationChanged = true
                        }
                    }

                    is Action.Unuse -> {
                        val index = model.modifiers.indexOfFirst { it.id == action.modifierId.evalInt(evaluator) }
                        if (index >= 0 && runtimeModifierEnabled[index]) {
                            runtimeModifierEnabled[index] = false
                            activationChanged = true
                        }
                    }

                    is Action.Grid -> gridCommands.add(action.call.resolve(evaluator))

                    else -> { /* cell actions are not allowed in a modifier */ }
                }
            } catch (_: Exception) {
                // invalid argument at run time: ignore the action
            }
        }
    }

    /** Parsed actions formulas, cached per behaviour (a Simulator is recreated on model change). */
    private val parsedActions = mutableMapOf<Behaviour, List<Action>>()

    private fun actionsFor(behaviour: Behaviour): List<Action> = parsedActions.getOrPut(behaviour) {
        runCatching { behaviour.actions.parseActionFormula() }.getOrDefault(emptyList())
    }

    /** Target index at offset (dx, dy) from [index], toroidal (consistent with [com.centyllion.model.Simulation.moveIndex]). */
    private fun offsetIndex(index: Int, dx: Int, dy: Int): Int {
        var target = index
        if (dx != 0) {
            target = simulation.moveIndex(target, if (dx < 0) Direction.Left else Direction.Right, abs(dx))
        }
        if (dy != 0) {
            target = simulation.moveIndex(target, if (dy < 0) Direction.Up else Direction.Down, abs(dy))
        }
        return target
    }

    private fun Expression.evalInt(evaluator: NetbioDynEvaluator): Int =
        evaluator.evaluate(this).float.roundToInt()

    private fun Expression.evalFloat(evaluator: NetbioDynEvaluator): Float =
        evaluator.evaluate(this).float

    /**
     * Executes the [behaviour]'s actions (if any) at [index].
     *
     * Agent mutations follow the same double-buffering rules as [transform];
     * field mutations are written into the next fields buffer (which becomes
     * current at the end of the step). Offsets are relative to the behaviour
     * cell and wrap around the grid (toroidal).
     *
     * An action failing at runtime (e.g. a negative field id) is ignored:
     * invalid formulas are reported as model problems in the editor.
     *
     * [ages] provides the `age(n)` formula values, 1-based "reactive"
     * numbering (same as the age-inheritance column): index 0 is the main
     * agent (0 for generation behaviours), index k (k >= 1) is the neighbour
     * of reaction k.
     */
    fun applyActions(behaviour: Behaviour, index: Int, ages: List<Int>) {
        val actions = actionsFor(behaviour)
        if (actions.isEmpty()) return

        val evaluator = expressionEvaluator
        val position = simulation.toPosition(index)
        evaluator.step = step.v
        evaluator.ages = ages
        evaluator.x = position.x.v
        evaluator.y = position.y.v
        evaluator.z = position.z.v
        // agent counts around the behaviour cell, from the start-of-step state
        // (same source as the predicate in applicable() and field formulas)
        val counts = mutableMapOf<Int, Int>()
        var emptyNeighbours = 0
        for (d in Direction.entries) {
            val id = currentAgents[simulation.moveIndex(index, d)]
            if (id >= 0) counts[id] = (counts[id] ?: 0) + 1 else emptyNeighbours += 1
        }
        counts[COUNT_ALL_AGENTS] = counts.values.sum()
        counts[COUNT_EMPTY_CELLS] = emptyNeighbours
        evaluator.agentCounts = counts
        evaluator.emptyCount = emptyNeighbours

        val targetAgents = if (parallelUpdate) nextAgents else agents
        val targetAges = if (parallelUpdate) nextAges else this.ages

        // a meta-action acts on the simulation: applying it once per cell would flood the
        // grid (a behaviour applicable on twelve cells would spawn twelve times N agents)
        val hasMetaAction = actions.any { isMetaAction(it) }
        val runMetaActions = !hasMetaAction || metaActionsDone.add(behaviour)

        for (action in actions) {
            if (isMetaAction(action) && !runMetaActions) continue
            try {
                when (action) {
                    is Action.Add -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        val target = offsetIndex(index, action.dx.evalInt(evaluator), action.dy.evalInt(evaluator))
                        if (targetAgents[target] < 0) {
                            targetAgents[target] = id
                            targetAges[target] = 0
                        }
                    }

                    is Action.Remove -> {
                        val id = action.grain.evalInt(evaluator)
                        val target = offsetIndex(index, action.dx.evalInt(evaluator), action.dy.evalInt(evaluator))
                        if (targetAgents[target] == id) {
                            targetAgents[target] = -1
                            targetAges[target] = -1
                        }
                    }

                    is Action.Move -> {
                        val id = action.grain.evalInt(evaluator)
                        val source = offsetIndex(index, action.dx1.evalInt(evaluator), action.dy1.evalInt(evaluator))
                        val target = offsetIndex(index, action.dx2.evalInt(evaluator), action.dy2.evalInt(evaluator))
                        if (targetAgents[source] == id) {
                            val movedAge = targetAges[source]
                            when {
                                targetAgents[target] < 0 -> {
                                    targetAgents[target] = id
                                    targetAges[target] = movedAge
                                    targetAgents[source] = -1
                                    targetAges[source] = -1
                                }
                                targetAgents[target] == id -> {
                                    // same type on the target: swap the two agents (and their ages)
                                    val targetAge = targetAges[target]
                                    targetAges[target] = movedAge
                                    targetAges[source] = targetAge
                                }
                            }
                        }
                    }

                    is Action.SetField -> {
                        val fieldId = action.field.evalInt(evaluator)
                        val target = offsetIndex(index, action.dx.evalInt(evaluator), action.dy.evalInt(evaluator))
                        nextFields[fieldId]?.set(target, action.value.evalFloat(evaluator).coerceIn(0f, 1f))
                    }

                    is Action.AddField -> {
                        val fieldId = action.field.evalInt(evaluator)
                        val target = offsetIndex(index, action.dx.evalInt(evaluator), action.dy.evalInt(evaluator))
                        nextFields[fieldId]?.let { array ->
                            array[target] = (array[target] + action.delta.evalFloat(evaluator)).coerceIn(0f, 1f)
                        }
                    }

                    Action.Pause -> pauseRequested = true

                    is Action.AddMany -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        var remaining = action.count.evalInt(evaluator)
                        var guard = 0
                        while (remaining > 0 && guard < targetAgents.size * 4) {
                            val slot = random.nextInt(index, targetAgents.size)
                            if (targetAgents[slot] < 0) {
                                targetAgents[slot] = id
                                targetAges[slot] = 0
                                remaining -= 1
                            }
                            guard += 1
                        }
                    }

                    is Action.AddRect -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        // N positions drawn among the empty cells of the zone (selection sampling)
                        val candidates = ArrayList<Int>()
                        forEachCellInRect(
                            action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                            action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                        ) { slot -> if (targetAgents[slot] < 0) candidates.add(slot) }
                        repeat(minOf(action.count.evalInt(evaluator), candidates.size)) {
                            val slot = candidates.removeAt(random.nextInt(index, candidates.size))
                            targetAgents[slot] = id
                            targetAges[slot] = 0
                        }
                    }

                    is Action.AddDisc -> {
                        val id = action.grain.evalInt(evaluator)
                        if (grainForId(id) == null) continue
                        // N positions drawn among the empty cells of the disc (selection sampling)
                        val candidates = ArrayList<Int>()
                        forEachCellInDisc(
                            action.x.evalInt(evaluator), action.y.evalInt(evaluator), action.radius.evalInt(evaluator)
                        ) { slot -> if (targetAgents[slot] < 0) candidates.add(slot) }
                        repeat(minOf(action.count.evalInt(evaluator), candidates.size)) {
                            val slot = candidates.removeAt(random.nextInt(index, candidates.size))
                            targetAgents[slot] = id
                            targetAges[slot] = 0
                        }
                    }

                    is Action.EraseRectAll -> {
                        forEachCellInRect(
                            action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                            action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                        ) { slot ->
                            targetAgents[slot] = -1
                            targetAges[slot] = -1
                        }
                    }

                    is Action.EraseDiscAll -> {
                        forEachCellInDisc(
                            action.x.evalInt(evaluator), action.y.evalInt(evaluator), action.radius.evalInt(evaluator)
                        ) { slot ->
                            targetAgents[slot] = -1
                            targetAges[slot] = -1
                        }
                    }

                    is Action.EraseRect -> {
                        val id = action.grain.evalInt(evaluator)
                        forEachCellInRect(
                            action.x1.evalInt(evaluator), action.y1.evalInt(evaluator),
                            action.x2.evalInt(evaluator), action.y2.evalInt(evaluator)
                        ) { slot ->
                            if (targetAgents[slot] == id) {
                                targetAgents[slot] = -1
                                targetAges[slot] = -1
                            }
                        }
                    }

                    is Action.EraseDisc -> {
                        val id = action.grain.evalInt(evaluator)
                        forEachCellInDisc(
                            action.x.evalInt(evaluator), action.y.evalInt(evaluator), action.radius.evalInt(evaluator)
                        ) { slot ->
                            if (targetAgents[slot] == id) {
                                targetAgents[slot] = -1
                                targetAges[slot] = -1
                            }
                        }
                    }

                    is Action.EraseAll -> {
                        val id = action.grain.evalInt(evaluator)
                        for (slot in targetAgents.indices) {
                            if (targetAgents[slot] == id) {
                                targetAgents[slot] = -1
                                targetAges[slot] = -1
                            }
                        }
                    }

                    is Action.Activate -> {
                        val behaviourIndex = behaviourIndexForId(action.behaviourId.evalInt(evaluator))
                        if (behaviourIndex >= 0 && !runtimeEnabled[behaviourIndex]) {
                            runtimeEnabled[behaviourIndex] = true
                            activationChanged = true
                        }
                    }

                    is Action.Inactivate -> {
                        val behaviourIndex = behaviourIndexForId(action.behaviourId.evalInt(evaluator))
                        if (behaviourIndex >= 0 && runtimeEnabled[behaviourIndex]) {
                            runtimeEnabled[behaviourIndex] = false
                            activationChanged = true
                        }
                    }

                    is Action.Use, is Action.Unuse, is Action.DrawLine, is Action.Grid -> {
                        /* meta-actions: they belong to the simulation modifiers */
                    }

                    is Action.ClearField -> {
                        val fieldId = action.field.evalInt(evaluator)
                        val target = offsetIndex(index, action.dx.evalInt(evaluator), action.dy.evalInt(evaluator))
                        nextFields[fieldId]?.set(target, 0f)
                    }
                }
            } catch (e: Exception) {
                // invalid argument at runtime: ignore the action (diagnosed in the editor)
            }
        }
    }

    fun setIdAtIndex(index: Int, id: Int) {
        currentAgents[index] = id
        ages[index] = 0
    }

    fun resetIdAtIndex(index: Int) {
        currentAgents[index] = -1
        ages[index] = -1
    }

    /** Returns all neighbours agents in all directions */
    fun neighbours(index: Int): List<Pair<Direction, Agent>> {
        val all = Direction.entries.toTypedArray()
        return List(all.size) { i ->
            val direction = all[i]
            direction to simulation.moveIndex(index, direction).let { id ->
                val fieldValues = if (model.fields.isEmpty()) emptyFloatArray
                else FloatArray(fieldMaxId + 1) { field(it).getOrNull(id) ?: 0f }
                Agent(id, direction, currentAgents[id], readAges[id], fieldValues)
            }
        }
    }

    fun grainsCounts(): MutableMap<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        for (i in currentAgents) {
            if (i >= 0) {
                result[i] = 1 + (result[i] ?: 0)
            }
        }
        return result
    }

    fun fieldAmounts(): Map<Int, Float> =
        fields.map { it.key to it.value.sum() }.toMap()

    val maxGrainCount: MutableMap<Int, Int> = grainsCounts()

    var maxFieldAmount: MutableMap<Int, Float> = fields.map { it.key to 0f }.toMap().toMutableMap()

}

/** Receives the applicable behaviours, indexed by the cells they occupy. */
private interface BehaviourCollector {
    fun add(cellIndex: Int, behaviour: ApplicableBehavior)
}

/** Collector used by the parallel path: one list per cell, merged afterwards. */
private class MapCollector(val map: MutableMap<Int, MutableList<ApplicableBehavior>>) : BehaviourCollector {
    override fun add(cellIndex: Int, behaviour: ApplicableBehavior) {
        map.getOrPut(cellIndex) { mutableListOf() }.add(behaviour)
    }
}

/**
 * Allocation-free collector: one head index per cell, chained through [next], all reused
 * across steps. Cells are registered in index order, so the conflict resolution is
 * deterministic (the former implementation iterated a HashMap).
 */
private class FastCollector(private val dataSize: Int) : BehaviourCollector {
    private val pool = ArrayList<ApplicableBehavior>()
    private val touched = ArrayList<Int>()
    private var head = IntArray(dataSize) { -1 }
    private var next = IntArray(256)

    fun reset() {
        for (cell in touched) head[cell] = -1
        touched.clear()
        pool.clear()
    }

    override fun add(cellIndex: Int, behaviour: ApplicableBehavior) {
        if (head[cellIndex] < 0) touched.add(cellIndex)
        if (pool.size == next.size) next = next.copyOf(next.size * 2)
        next[pool.size] = head[cellIndex]
        head[cellIndex] = pool.size
        pool.add(behaviour)
    }

    /** Keeps one behaviour per cell at random, drops the others, and deduplicates. */
    fun resolve(random: SimulationRandom): List<ApplicableBehavior> {
        val excluded = HashSet<ApplicableBehavior>()
        for (cellIndex in touched) {
            var pointer = head[cellIndex]
            var count = 0
            while (pointer >= 0) {
                count += 1
                pointer = next[pointer]
            }
            if (count <= 1) continue
            val kept = random.nextInt(cellIndex, count)
            pointer = head[cellIndex]
            var index = 0
            while (pointer >= 0) {
                if (index != kept) excluded.add(pool[pointer])
                index += 1
                pointer = next[pointer]
            }
        }
        val result = LinkedHashSet<ApplicableBehavior>()
        for (behaviour in pool) if (behaviour !in excluded) result.add(behaviour)
        return result.toList()
    }
}
