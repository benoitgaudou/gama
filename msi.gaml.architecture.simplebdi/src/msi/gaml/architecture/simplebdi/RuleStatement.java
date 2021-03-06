/*********************************************************************************************
 * 
 *
 * 'RuleStatement.java', in plugin 'msi.gaml.architecture.simplebdi', is part of the source code of the 
 * GAMA modeling and simulation platform.
 * (c) 2007-2014 UMI 209 UMMISCO IRD/UPMC & Partners
 * 
 * Visit https://code.google.com/p/gama-platform/ for license information and developers contact.
 * 
 * 
 **********************************************************************************************/

package msi.gaml.architecture.simplebdi;

import java.util.List;

import msi.gama.common.interfaces.IKeyword;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.example;
import msi.gama.precompiler.GamlAnnotations.facet;
import msi.gama.precompiler.GamlAnnotations.facets;
import msi.gama.precompiler.GamlAnnotations.inside;
import msi.gama.precompiler.GamlAnnotations.symbol;
import msi.gama.precompiler.IConcept;
import msi.gama.precompiler.ISymbolKind;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.descriptions.IDescription;
import msi.gaml.expressions.IExpression;
import msi.gaml.operators.Cast;
import msi.gaml.statements.AbstractStatement;
import msi.gaml.types.IType;
import msi.gaml.types.Types;

@symbol(name = RuleStatement.RULE, kind = ISymbolKind.SINGLE_STATEMENT, with_sequence = false, concept = {
		IConcept.BDI })
@inside(kinds = { ISymbolKind.SPECIES, ISymbolKind.MODEL })
@facets(value = {
		@facet(name = RuleStatement.BELIEF, type = PredicateType.id, optional = true, doc = @doc("The mandatory belief")),
		@facet(name = RuleStatement.DESIRE, type = PredicateType.id, optional = true, doc = @doc("The mandatory desire")),
		@facet(name = RuleStatement.EMOTION, type = EmotionType.id, optional = true, doc = @doc("The mandatory emotion")),
		@facet(name = RuleStatement.UNCERTAINTY, type = PredicateType.id, optional = true, doc = @doc("The mandatory uncertainty")),
		@facet(name = RuleStatement.NEW_DESIRE, type = PredicateType.id, optional = true, doc = @doc("The desire that will be added")),
		@facet(name = RuleStatement.NEW_BELIEF, type = PredicateType.id, optional = true, doc = @doc("The belief that will be added")),
		@facet(name = RuleStatement.NEW_EMOTION, type = EmotionType.id, optional = true, doc = @doc("The emotion that will be added")),
		@facet(name = RuleStatement.NEW_UNCERTAINTY, type = PredicateType.id, optional = true, doc = @doc("The uncertainty that will be added")),
		@facet(name = RuleStatement.NEW_DESIRES, type = IType.LIST, of=PredicateType.id, optional = true, doc = @doc("The desire that will be added")),
		@facet(name = RuleStatement.NEW_BELIEFS, type = IType.LIST, of=PredicateType.id, optional = true, doc = @doc("The belief that will be added")),
		@facet(name = RuleStatement.NEW_EMOTIONS, type = IType.LIST, of=EmotionType.id, optional = true, doc = @doc("The emotion that will be added")),
		@facet(name = RuleStatement.NEW_UNCERTAINTIES, type = IType.LIST, of=PredicateType.id, optional = true, doc = @doc("The uncertainty that will be added")),
		@facet(name = RuleStatement.REMOVE_BELIEFS, type = IType.LIST, of = PredicateType.id, optional = true, doc = @doc("The belief that will be removed")),
		@facet(name = RuleStatement.REMOVE_DESIRES, type = IType.LIST, of = PredicateType.id, optional = true, doc = @doc("The desire that will be removed")),
		@facet(name = RuleStatement.REMOVE_EMOTIONS, type = IType.LIST, of = EmotionType.id, optional = true, doc = @doc("The emotion that will be removed")),
		@facet(name = RuleStatement.REMOVE_UNCERTAINTIES, type = IType.LIST, of = PredicateType.id, optional = true, doc = @doc("The uncertainty that will be removed")),
		@facet(name = RuleStatement.REMOVE_BELIEF, type = PredicateType.id, optional = true, doc = @doc("The belief that will be removed")),
		@facet(name = RuleStatement.REMOVE_DESIRE, type = PredicateType.id, optional = true, doc = @doc("The desire that will be removed")),
		@facet(name = RuleStatement.REMOVE_INTENTION, type = PredicateType.id, optional = true, doc = @doc("The intention that will be removed")),
		@facet(name = RuleStatement.REMOVE_EMOTION, type = EmotionType.id, optional = true, doc = @doc("The emotion that will be removed")),
		@facet(name = RuleStatement.REMOVE_UNCERTAINTY, type = PredicateType.id, optional = true, doc = @doc("The uncertainty that will be removed")),
		@facet(name = IKeyword.WHEN, type = IType.BOOL, optional = true, doc = @doc(" ")),
		@facet(name = RuleStatement.THRESHOLD, type = IType.FLOAT, optional = true, doc = @doc("Threshold linked to the emotion.")),
		@facet(name = RuleStatement.PRIORITY, type = { IType.FLOAT,
				IType.INT }, optional = true, doc = @doc("The priority of the predicate added as a desire")),
		@facet(name = IKeyword.NAME, type = IType.ID, optional = true, doc = @doc("The name of the rule")) }, omissible = IKeyword.NAME)
@doc(value = "enables to add a desire or a belief or to remove a belief, a desire or an intention if the agent gets the belief or/and desire or/and condition mentioned.", examples = {
		@example("rule belief: new_predicate(\"test\") when: flip(0.5) new_desire: new_predicate(\"test\")") })
public class RuleStatement extends AbstractStatement {

	public static final String RULE = "rule";
	public static final String BELIEF = "belief";
	public static final String DESIRE = "desire";
	public static final String EMOTION = "emotion";
	public static final String UNCERTAINTY = "uncertainty";
	public static final String NEW_DESIRE = "new_desire";
	public static final String NEW_BELIEF = "new_belief";
	public static final String NEW_EMOTION = "new_emotion";
	public static final String NEW_UNCERTAINTY = "new_uncertainty";
	public static final String REMOVE_BELIEF = "remove_belief";
	public static final String REMOVE_DESIRE = "remove_desire";
	public static final String REMOVE_INTENTION = "remove_intention";
	public static final String REMOVE_EMOTION = "remove_emotion";
	public static final String REMOVE_UNCERTAINTY = "remove_uncertainty";
	public static final String NEW_DESIRES = "new_desires";
	public static final String NEW_BELIEFS = "new_beliefs";
	public static final String NEW_EMOTIONS = "new_emotions";
	public static final String NEW_UNCERTAINTIES = "new_uncertainties";
	public static final String REMOVE_BELIEFS = "remove_beliefs";
	public static final String REMOVE_DESIRES = "remove_desires";
	public static final String REMOVE_EMOTIONS = "remove_emotions";
	public static final String REMOVE_UNCERTAINTIES = "remove_uncertainties";
	public static final String PRIORITY = "priority";
	public static final String THRESHOLD = "threshold";

	final IExpression when;
	final IExpression belief;
	final IExpression desire;
	final IExpression emotion;
	final IExpression uncertainty;
	final IExpression newBelief;
	final IExpression newDesire;
	final IExpression newEmotion;
	final IExpression newUncertainty;
	final IExpression removeBelief;
	final IExpression removeDesire;
	final IExpression removeIntention;
	final IExpression removeEmotion;
	final IExpression removeUncertainty;
	final IExpression newBeliefs;
	final IExpression newDesires;
	final IExpression newEmotions;
	final IExpression newUncertainties;
	final IExpression removeBeliefs;
	final IExpression removeDesires;
	final IExpression removeEmotions;
	final IExpression removeUncertainties;
	final IExpression priority;
	final IExpression threshold;

	public RuleStatement(final IDescription desc) {
		super(desc);
		when = getFacet(IKeyword.WHEN);
		belief = getFacet(RuleStatement.BELIEF);
		desire = getFacet(RuleStatement.DESIRE);
		emotion = getFacet(RuleStatement.EMOTION);
		uncertainty = getFacet(RuleStatement.UNCERTAINTY);
		newBelief = getFacet(RuleStatement.NEW_BELIEF);
		newDesire = getFacet(RuleStatement.NEW_DESIRE);
		newEmotion = getFacet(RuleStatement.NEW_EMOTION);
		newUncertainty = getFacet(RuleStatement.NEW_UNCERTAINTY);
		removeBelief = getFacet(RuleStatement.REMOVE_BELIEF);
		removeDesire = getFacet(RuleStatement.REMOVE_DESIRE);
		removeIntention = getFacet(RuleStatement.REMOVE_INTENTION);
		removeEmotion = getFacet(RuleStatement.REMOVE_EMOTION);
		removeUncertainty = getFacet(RuleStatement.REMOVE_UNCERTAINTY);
		newBeliefs = getFacet(RuleStatement.NEW_BELIEFS);
		newDesires = getFacet(RuleStatement.NEW_DESIRES);
		newEmotions = getFacet(RuleStatement.NEW_EMOTIONS);
		newUncertainties = getFacet(RuleStatement.NEW_UNCERTAINTIES);
		removeBeliefs = getFacet(RuleStatement.REMOVE_BELIEFS);
		removeDesires = getFacet(RuleStatement.REMOVE_DESIRES);
		removeEmotions = getFacet(RuleStatement.REMOVE_EMOTIONS);
		removeUncertainties = getFacet(RuleStatement.REMOVE_UNCERTAINTIES);
		priority = getFacet(RuleStatement.PRIORITY);
		threshold = getFacet(RuleStatement.THRESHOLD);
	}

	@Override
	protected Object privateExecuteIn(final IScope scope) throws GamaRuntimeException {
		if (newBelief == null && newDesire == null && newEmotion == null && newUncertainty == null
				&& removeBelief == null && removeDesire == null && removeIntention == null && removeEmotion == null
				&& removeUncertainty == null)
			return null;
		if (when == null || Cast.asBool(scope, when.value(scope))) {
			if (belief == null || SimpleBdiArchitecture.hasBelief(scope, (Predicate) belief.value(scope))) {
				if (desire == null || SimpleBdiArchitecture.hasDesire(scope, (Predicate) desire.value(scope))) {
					if (uncertainty == null
							|| SimpleBdiArchitecture.hasUncertainty(scope, (Predicate) uncertainty.value(scope))) {
						if (emotion == null
								|| SimpleBdiArchitecture.hasEmotion(scope, (Emotion) emotion.value(scope))) {
							if (threshold == null || emotion != null && threshold != null
									&& SimpleBdiArchitecture.getEmotion(scope,
											(Emotion) emotion.value(scope)).intensity >= (Double) threshold
													.value(scope)) {
								if (newDesire != null) {
									final Predicate newDes = (Predicate) newDesire.value(scope);
									if (priority != null) {
										newDes.setPriority(Cast.asFloat(scope, priority.value(scope)));
									}
									SimpleBdiArchitecture.addDesire(scope, null, newDes);
								}
								if (newBelief != null) {
									final Predicate newBel = (Predicate) newBelief.value(scope);
									SimpleBdiArchitecture.addBelief(scope, newBel);
								}
								if (newEmotion != null) {
									final Emotion newEmo = (Emotion) newEmotion.value(scope);
									SimpleBdiArchitecture.addEmotion(scope, newEmo);
								}
								if (newUncertainty != null) {
									final Predicate newUncert = (Predicate) newUncertainty.value(scope);
									SimpleBdiArchitecture.addUncertainty(scope, newUncert);
								}
								if (removeBelief != null) {
									final Predicate removBel = (Predicate) removeBelief.value(scope);
									SimpleBdiArchitecture.removeBelief(scope, removBel);
								}
								if (removeDesire != null) {
									final Predicate removeDes = (Predicate) removeDesire.value(scope);
									SimpleBdiArchitecture.removeDesire(scope, removeDes);
								}
								if (removeIntention != null) {
									final Predicate removeInt = (Predicate) removeIntention.value(scope);
									SimpleBdiArchitecture.removeIntention(scope, removeInt);
								}
								if (removeEmotion != null) {
									final Emotion removeEmo = (Emotion) removeEmotion.value(scope);
									SimpleBdiArchitecture.removeEmotion(scope, removeEmo);
								}
								if (removeUncertainty != null) {
									final Predicate removUncert = (Predicate) removeUncertainty.value(scope);
									SimpleBdiArchitecture.removeUncertainty(scope, removUncert);
								}
								
								
								if (newDesires != null) {
									final List<Predicate> newDess = (List<Predicate>) newDesires.value(scope);
									if (priority != null) {
										for (Predicate newDes : newDess) newDes.setPriority(Cast.asFloat(scope, priority.value(scope)));
									}
									for (Predicate newDes : newDess) SimpleBdiArchitecture.addDesire(scope, null, newDes);
								}
								if (newBeliefs != null) {
									final List<Predicate> newBels = (List<Predicate>) newBeliefs.value(scope);
									for (Predicate newBel : newBels) SimpleBdiArchitecture.addBelief(scope, newBel);
								}
								if (newEmotions != null) {
									final List<Emotion> newEmos = (List<Emotion>) newEmotions.value(scope);
									for (Emotion newEmo : newEmos) SimpleBdiArchitecture.addEmotion(scope, newEmo);
								}
								if (newUncertainties != null) {
									final List<Predicate> newUncerts = (List<Predicate>) newUncertainties.value(scope);
									for (Predicate newUncert : newUncerts) SimpleBdiArchitecture.addUncertainty(scope, newUncert);
								}
								if (removeBeliefs != null) {
									final List<Predicate> removBels = (List<Predicate>) removeBeliefs.value(scope);
									for (Predicate removBel : removBels)SimpleBdiArchitecture.removeBelief(scope, removBel);
								}
								if (removeDesires != null) {
									final List<Predicate> removeDess = (List<Predicate>) removeDesires.value(scope);
									for (Predicate removeDes : removeDess) SimpleBdiArchitecture.removeDesire(scope, removeDes);
								}
								if (removeEmotions != null) {
									final List<Emotion> removeEmos = (List<Emotion>) removeEmotions.value(scope);
									for (Emotion removeEmo : removeEmos)SimpleBdiArchitecture.removeEmotion(scope, removeEmo);
								}
								if (removeUncertainties != null) {
									final List<Predicate> removUncerts = (List<Predicate>) removeUncertainties.value(scope);
									for (Predicate removUncert : removUncerts) SimpleBdiArchitecture.removeUncertainty(scope, removUncert);
								}
							}
						}
					}
				}
			}
		}
		return null;
	}
}
