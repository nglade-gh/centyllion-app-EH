package com.centyllion.model

import com.centyllion.expression.parseExpression
import com.centyllion.i18n.Locale

/** Ids of the agents of this model: a grain list names them explicitly. */
private fun GrainModel.grainIds(): Set<Int> = grains.mapTo(mutableSetOf()) { it.id }

/**
 * Reports, on the formula that uses them, the four set predicates over the neighbourhood when the
 * model declares more agent types than their 32-bit mask can hold ([NEIGHBOUR_SET_MAX_TYPES]).
 *
 * The mask is an ordinary number at run time, so an overflowing model would simply answer a wrong
 * result: this diagnostic is the only place where the limit can be seen, hence the explicit
 * message rather than a silent truncation.
 */
private fun addNeighbourSetLimitProblem(
    source: ModelElement,
    property: String,
    formula: String,
    model: GrainModel,
    locale: Locale,
    result: MutableList<Problem>
) {
    if (model.grains.size <= NEIGHBOUR_SET_MAX_TYPES) return
    val used = runCatching { formula.parseExpression().neighbourSetUsed() }.getOrDefault(false)
    if (!used) return
    result.add(
        Problem(
            source, property, locale.i18n(
                "The neighbourhood set predicates (hasAnyOf, hasAllOf, hasOnly, hasExactly) encode at most %0 agent types, but this model declares %1: the mask cannot represent every type and the test would answer a wrong result. Reduce the model to %0 types or use count() instead.",
                NEIGHBOUR_SET_MAX_TYPES, model.grains.size
            )
        )
    )
}

fun GrainModel.diagnose(locale: Locale): List<Problem> = buildList {
    grains.forEach { it.diagnose(this@diagnose, locale, this) }
    behaviours.forEach { it.diagnose(this@diagnose, locale, this) }
    fields.forEach { it.diagnose(this@diagnose, locale, this) }
    modifiers.forEach { it.diagnose(locale, this) }
}

fun Grain.hasNoError(model: GrainModel, locale: Locale) =
    buildList { diagnose(model,locale, this) }.isEmpty()

fun Grain.diagnose(model: GrainModel, locale: Locale, result: MutableList<Problem>) {
    if (halfLife < 0) {
        result.add(Problem(this, "Half-life", locale.i18n("Half-life must be positive or zero")))
    }

    if (movementProbability < 0.0 || movementProbability > 1.0) {
        result.add(Problem(this, "Speed", locale.i18n("Speed must be between 0 and 1")))
    }

    fieldProductions.forEach { entry ->
        if (entry.value < -1 || entry.value > 1) {
            val field = model.fieldForId(entry.key)
            result.add(Problem(this, "Field production", locale.i18n("Field production for %0 must be between -1 and 1", field?.name ?: "")))
        }

        if ((fieldPermeable[entry.key] ?: 1f) <= 0f && entry.value != 0f) {
            val field = model.fieldForId(entry.key)
            result.add(Problem(this, "Field permeability", locale.i18n("Field permeability will prevent production for %0", field?.name ?: "")))
        }
    }

    fieldInfluences.forEach { entry ->
        if (entry.value < -1 || entry.value > 1) {
            val field = model.fieldForId(entry.key)
            result.add(Problem(this, "Field influence", locale.i18n("Field influence for %0 must be between -1 and 1", field?.name ?: "")))
        }
    }

    fieldPermeable.forEach {entry ->
        if (entry.value < 0 || entry.value > 1) {
            val field = model.fieldForId(entry.key)
            Problem(this, "Field permeability", locale.i18n("Field permeability for %0 must be between 0 and 1", field?.name ?: ""))
        }
    }
}

fun Behaviour.hasNoError(model: GrainModel, locale: Locale) =
    buildList { diagnose(model,locale, this) }.isEmpty()

fun Behaviour.diagnose(model: GrainModel, locale: Locale, result: MutableList<Problem>) {
    if (mainReactiveId < -1) {
        val message = if (mainReactiveId == ANY_AGENT)
            "Any agent is only available for the reactions of a behaviour, never for its main reactive"
        else "Behaviour must have a main reactive"
        result.add(Problem(this, "Main Reactive", locale.i18n(message)))
    }

    if (probability < 0.0 || probability > 1.0) {
        result.add(Problem(this, "Speed", locale.i18n("Speed must be between 0 and 1")))
    }

    if (agePredicate.constant < 0) {
        result.add(Problem(this, "Age Predicate", locale.i18n("Age predicate value must be positive or zero")))
    }

    agePredicates.forEach { predicate ->
        if (predicate.constant < 0) {
            result.add(Problem(this, "Age Predicate", locale.i18n("Age predicate value must be positive or zero")))
        }
    }

    fieldPredicates.forEach { predicate ->
        if (predicate.second.constant < 0f || predicate.second.constant > 1f) {
            val field = model.fieldForId(predicate.first)
            result.add(Problem(this, "Threshold", locale.i18n("Field threshold value for %0 must be between 0 and 1", field?.name ?: "")))
        }
    }

    agentCountPredicates.forEach { predicate ->
        if (predicate.grainId >= 0 && model.grainForId(predicate.grainId) == null) {
            result.add(Problem(this, "Agent Count", locale.i18n("Grain with id %0 doesn't exist for agent count predicate", predicate.grainId)))
        }
        if (predicate.count < 0) {
            result.add(Problem(this, "Agent Count", locale.i18n("Agent count predicate value must be positive or zero")))
        }
    }

    if (formula.isNotBlank()) {
        val error = validatePredicateFormula(formula, model.grainIds())
        if (error != null) {
            result.add(Problem(this, "Predicate", error))
        } else {
            addNeighbourSetLimitProblem(this, "Predicate", formula, model, locale, result)
        }
    }

    if (actions.isNotBlank()) {
        val error = validateActionFormula(actions)
        if (error != null) {
            result.add(Problem(this, "Actions", error))
        }
    }

    reaction.forEachIndexed { index, reaction ->
        reaction.diagnose(model, this, index+1, locale, result)
    }

    // Check for meaningless behaviour: None -> None
    if (mainReactiveId == -1 && mainProductId == -1 && reaction.all { it.reactiveId == -1 && it.productId == -1 }) {
        // with actions the behaviour is not inert: it acts on the simulation (SetField…)
        // without producing any agent, which is legitimate but worth an explicit warning
        val message = if (actions.isBlank()) "Behaviour has no effect (None -> None)"
        else "Behaviour produces no agent (None -> None): its actions alone act on the simulation, for example SetField(0, 0, 0, 1), possibly under a predicate on the simulation step."
        result.add(Problem(this, "Meaningless", locale.i18n(message), warning = actions.isNotBlank()))
    }
}

fun Reaction.diagnose(model: GrainModel, behaviour: Behaviour, index: Int, locale: Locale, result: MutableList<Problem>) {
    // the reactive may be a grain id, -1 (an empty neighbour cell) or ANY_AGENT, which names
    // any occupied neighbour whatever its grain
    when {
        reactiveId == ANY_AGENT -> Unit
        reactiveId == -1 -> Unit
        reactiveId >= 0 -> if (model.grainForId(reactiveId) == null) {
            result.add(Problem(behaviour, "Reactive Id", locale.i18n("Grain with id %0 doesn't exist for reactive %1", reactiveId, index)))
        }
        else -> result.add(
            Problem(behaviour, "Reactive Id", locale.i18n(
                "Invalid id %0 for reactive %1: a reactive is a grain id, none (-1) or any agent (%2)",
                reactiveId, index, ANY_AGENT
            ))
        )
    }

    // a product is always a concrete grain (or -1 for no product): "any agent" names a
    // neighbour to replace, so it has no meaning on the product side
    when {
        productId == ANY_AGENT -> result.add(
            Problem(behaviour, "Product Id", locale.i18n(
                "Any agent can only be a reactive of a reaction, never its product (reactive %0)", index
            ))
        )
        productId >= 0 -> if (model.grainForId(productId) == null) {
            result.add(Problem(behaviour, "Product Id", locale.i18n("Grain with id %0 doesn't exist for reactive %1", productId, index)))
        }
        productId == -1 -> Unit
        else -> result.add(
            Problem(behaviour, "Product Id", locale.i18n(
                "Invalid id %0 for the product of reactive %1: a product is a grain id or none (-1)", productId, index
            ))
        )
    }

    if (allowedDirection.isEmpty()) {
        result.add(Problem(behaviour, "Direction", locale.i18n("No direction allowed for reactive %0", index)))
    }
}

fun Field.diagnose(model: GrainModel, locale: Locale, result: MutableList<Problem>) {
    if (halfLife < 0) {
        result.add(Problem(this, "Half-life", locale.i18n("Half-life must be positive or zero")))
    }

    if (speed < 0.0 || speed > 1.0) {
        result.add(Problem(this, "Speed", locale.i18n("Speed must be between 0 and 1")))
    }

    if (formula.isNotBlank()) {
        val error = validateNumericFormula(formula, model.grainIds())
        if (error != null) {
            result.add(Problem(this, "Formula", error))
        } else {
            addNeighbourSetLimitProblem(this, "Formula", formula, model, locale, result)
        }
    }
}

/**
 * Diagnoses a simulation modifier: its condition (the only place where the state tests are
 * allowed) and its actions (meta-actions only). Without this, a formula the simulator cannot
 * parse would be silently ignored at run time instead of being reported in the editor.
 */
fun SimulationModifier.diagnose(locale: Locale, result: MutableList<Problem>) {
    if (formula.isNotBlank()) {
        validateModifierCondition(formula)?.let { result.add(Problem(this, "Condition", it)) }
    }
    if (actions.isNotBlank()) {
        validateModifierActions(actions)?.let { result.add(Problem(this, "Actions", it)) }
    }
}
