package com.centyllion.model

import com.centyllion.expression.v
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

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
    }
}

class Simulator(
    val model: GrainModel,
    val simulation: Simulation
) {
    val random = Random.Default

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
    private class ThreadContext(
        val evaluator: NetbioDynEvaluator,
        val evaluators: Map<Int, FieldEvaluator>
    )

    private val mainContext = ThreadContext(expressionEvaluator, evaluators)

    private val threadContexts: MutableList<ThreadContext> = mutableListOf()

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
    }

    /** Executes one step and returns the applied behaviours and the dead indexes. */
    fun oneStep(): Pair<Collection<ApplicableBehavior>, Collection<Int>> {
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
        // applies agents dying process
        val currentCount = mutableMapOf<Grain, Int>().apply {
            model.grains.forEach { this[it] = 0 }
        }
        val currentFieldAmounts = mutableMapOf<Field, Float>().apply {
            model.fields.forEach { this[it] = 0f }
        }

        // defines values for fields to avoid dynamic call each time
        val fields = fields
        val nextFields = nextFields

        val dead = mutableListOf<Int>()

        // all will contain index of agents as keys associated to a list of applicable behaviors
        // if an agent doesn't contain any applicable behavior it will have an empty list.
        // if an agent can't move and doesn't contain any applicable behavior, it won't be present in the map
        val all = mutableMapOf<Int, MutableList<ApplicableBehavior>>()

        if (shouldParallelize()) {
            runParallelStep(all, currentCount, currentFieldAmounts, dead, fields, nextFields)
        } else {
            val context = mainContext
            for (i in 0 until simulation.dataSize) {
                processCell(i, context, all, currentCount, currentFieldAmounts, dead, random, fields, nextFields)
            }
        }

        // filters behaviors that are concurrent to preserve the matter
        val values = all.values.asSequence()
        val toExclude = values
            .filter { it.size > 1 }
            .flatMap { (it - it[random.nextInt(it.size)]).asSequence() }
            .toSet()
        val toExecute = (values.flatten() - toExclude).toSet()
        toExecute.forEach { it.apply(this) }

        // stores count for each grain
        currentCount.forEach {
            grainCountHistory[it.key]?.add(it.value)
            maxGrainCount[it.key.id] = max(maxGrainCount[it.key.id] ?: 0, it.value)
        }

        // stores field amounts
        currentFieldAmounts.forEach {
            fieldAmountHistory[it.key]?.add(it.value)
            maxFieldAmount[it.key.id] = max(maxFieldAmount[it.key.id] ?: 0f, it.value)
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
        val count: Map<Grain, Int>,
        val fieldAmounts: Map<Field, Float>,
        val dead: List<Int>
    )

    /** Runs the per-cell work on multiple threads (parallel update mode, large grids).
     *  Each block builds its own accumulators; results are merged afterwards. */
    private fun runParallelStep(
        all: MutableMap<Int, MutableList<ApplicableBehavior>>,
        currentCount: MutableMap<Grain, Int>,
        currentFieldAmounts: MutableMap<Field, Float>,
        dead: MutableList<Int>,
        fields: Map<Int, FloatArray>,
        nextFields: Map<Int, FloatArray>
    ) {
        val blocks = splitIntoBlocks(simulation.dataSize, recommendedThreads())
        ensureThreadContexts(blocks.size)
        val executor = executorFor(blocks.size)

        val results = executor.mapBlocks(blocks) { block ->
            val context = threadContexts[block.index]
            val localAll = mutableMapOf<Int, MutableList<ApplicableBehavior>>()
            val localCount = mutableMapOf<Grain, Int>().apply {
                model.grains.forEach { this[it] = 0 }
            }
            val localFieldAmounts = mutableMapOf<Field, Float>().apply {
                model.fields.forEach { this[it] = 0f }
            }
            val localDead = mutableListOf<Int>()
            val localRandom = Random(block.index * 31 + step)
            for (i in block.range) {
                processCell(i, context, localAll, localCount, localFieldAmounts, localDead, localRandom, fields, nextFields)
            }
            BlockResult(localAll, localCount, localFieldAmounts, localDead)
        }

        // merge per-block results
        results.forEach { r ->
            r.all.forEach { (index, behaviours) ->
                all.getOrPut(index) { mutableListOf() }.addAll(behaviours)
            }
            r.count.forEach { (grain, count) ->
                currentCount[grain] = (currentCount[grain] ?: 0) + count
            }
            r.fieldAmounts.forEach { (field, amount) ->
                currentFieldAmounts[field] = (currentFieldAmounts[field] ?: 0f) + amount
            }
            dead.addAll(r.dead)
        }
    }

    /** Processes one cell: death, ageing, behaviour deliberation and field update. */
    private fun processCell(
        i: Int,
        context: ThreadContext,
        all: MutableMap<Int, MutableList<ApplicableBehavior>>,
        currentCount: MutableMap<Grain, Int>,
        currentFieldAmounts: MutableMap<Field, Float>,
        dead: MutableList<Int>,
        random: Random,
        fields: Map<Int, FloatArray>,
        nextFields: Map<Int, FloatArray>
    ) {
        val position = simulation.toPosition(i)
        context.evaluator.step = step.v
        context.evaluator.x = position.x.v
        context.evaluator.y = position.y.v
        context.evaluator.z = position.z.v

        val grain = grainAtIndex(i)
        if (grain != null) {
            // does the grain dies ?
            if (grain.halfLife > 0.0 && random.nextDouble() <= grain.deathProbability) {
                // it dies, doesn't count
                transform(i, i, null)
                dead.add(i)
            } else {
                currentCount[grain] = currentCount.getOrElse(grain) { 0 } + 1
                ageGrain(i)

                if (reactiveGrains.contains(grain)) {
                    // a grain is present, a behaviour can be triggered
                    val age = ageAtIndex(i)
                    val fieldValues = fieldsAtIndex(i)

                    // find all neighbours
                    val neighbours = neighbours(i)

                    // searches for applicable behaviours and filters by probability
                    val applicable = allBehaviours.filter {
                        it.applicable(grain, age, fieldValues, neighbours, position) && random.nextDouble() < speeds[it]!!
                    }

                    // selects behaviour if any is applicable
                    if (applicable.isNotEmpty()) {
                        // selects one at random
                        val behaviour = applicable[random.nextInt(applicable.size)]

                        // for each reaction, find all possible reactives
                        val possibleReactions = behaviour.reaction
                            .map { reaction ->
                                neighbours.filter { (d, a) ->
                                    reaction.reactiveId == a.reactiveId && reaction.allowedDirection.contains(d)
                                }
                            }

                        // combines possibilities
                        val allCombinations = possibleReactions.allCombinations()

                        val chosenCombination = when {
                            // optimized case when no field is used for the behaviour
                            model.fields.isEmpty() || !behaviour.fieldInfluenced -> {
                                random.nextInt(allCombinations.size)
                            }

                            else -> {
                                // computes weight of each combination by adding the influence of all neighbours for a combination
                                val influence = FloatArray(allCombinations.size) { c ->
                                    var sum = 0f
                                    allCombinations[c].forEach { (_, agent) ->
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
                                val probability = random.nextDouble(current.toDouble())
                                influence.indexOfFirst { probability < it }
                            }
                        }
                        // registers behaviour for concurrency
                        val usedNeighbours = allCombinations[chosenCombination].map { it.second }
                        val behavior = ApplicableBehavior(i, readAges[i], behaviour, usedNeighbours)
                        // adds applicable to current agent
                        all.getOrPut(i) { mutableListOf() }.add(behavior)
                        // adds applicable to neighbours
                        usedNeighbours.forEach { all.getOrPut(it.index) { mutableListOf() }.add(behavior) }
                    }
                }
            }
        } else {
            // empty cell: check for generation behaviours (mainReactiveId == -1)
            val fieldValues = fieldsAtIndex(i)
            val neighbours = neighbours(i)

            // for generation, use the simulation step as the "age" value
            val applicable = allBehaviours.filter {
                it.mainReactiveId == -1 && it.applicable(null, step, fieldValues, neighbours, position) && random.nextDouble() < speeds[it]!!
            }

            if (applicable.isNotEmpty()) {
                // selects one at random
                val behaviour = applicable[random.nextInt(applicable.size)]

                // for each reaction, find all possible reactives
                val possibleReactions = behaviour.reaction
                    .map { reaction ->
                        neighbours.filter { (d, a) ->
                            reaction.reactiveId == a.reactiveId && reaction.allowedDirection.contains(d)
                        }
                    }

                // combines possibilities
                val allCombinations = possibleReactions.allCombinations()

                val chosenCombination = when {
                    model.fields.isEmpty() || !behaviour.fieldInfluenced -> {
                        random.nextInt(allCombinations.size)
                    }
                    else -> {
                        val influence = FloatArray(allCombinations.size) { c ->
                            var sum = 0f
                            allCombinations[c].forEach { (_, agent) ->
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
                            val translated = (influence[index] - min + 1f).pow(6)
                            influence[index] = current + translated
                            current += translated
                        }

                        val probability = random.nextDouble(current.toDouble())
                        influence.indexOfFirst { probability < it }
                    }
                }
                val usedNeighbours = allCombinations[chosenCombination].map { it.second }
                val behavior = ApplicableBehavior(i, 0, behaviour, usedNeighbours)
                all.getOrPut(i) { mutableListOf() }.add(behavior)
                usedNeighbours.forEach { all.getOrPut(it.index) { mutableListOf() }.add(behavior) }
            }
        }

        // compute fields values for step
        // count() in field formulas: number of agents in the 8 adjacent cells
        if (fieldsRequireCount) {
            val counts = mutableMapOf<Int, Int>()
            for (d in Direction.entries) {
                val id = currentAgents[simulation.moveIndex(i, d)]
                if (id >= 0) counts[id] = (counts[id] ?: 0) + 1
            }
            context.evaluator.agentCounts = counts
        }
        model.fields.forEach { field ->
            val current = fields[field.id]
            val next = nextFields[field.id]
            val count = field.allowedDirection.size

            var permeableSum = 0f
            for (allowed in field.allowedDirection) {
                val index = simulation.moveIndex(i, allowed)
                permeableSum += (grainAtIndex(index)?.fieldPermeable?.get(field.id) ?: 1f)
            }

            if (next != null && current != null) {
                var diffusionSum = 0f
                for (opposite in field.oppositeDirections) {
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
                currentFieldAmounts[field] = (currentFieldAmounts[field] ?: 0f) + next[i]
            }
        }
    }

    fun reset() {
        initialAgents.copyInto(agents)
        for (i in ages.indices) {
            ages[i] = if (agents[i] != -1) 0 else -1
            fields.forEach { it.value[i] = minField }
        }
        // Also reset the parallel buffers so they don't hold stale state
        agents.copyInto(nextAgents)
        ages.copyInto(nextAges)

        step = 0
        resetCount()
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
