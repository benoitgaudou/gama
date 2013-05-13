/*
 * GAMA - V1.4 http://gama-platform.googlecode.com
 * 
 * (c) 2007-2011 UMI 209 UMMISCO IRD/UPMC & Partners (see below)
 * 
 * Developers :
 * 
 * - Alexis Drogoul, UMI 209 UMMISCO, IRD/UPMC (Kernel, Metamodel, GAML), 2007-2012
 * - Vo Duc An, UMI 209 UMMISCO, IRD/UPMC (SWT, multi-level architecture), 2008-2012
 * - Patrick Taillandier, UMR 6228 IDEES, CNRS/Univ. Rouen (Batch, GeoTools & JTS), 2009-2012
 * - Beno�t Gaudou, UMR 5505 IRIT, CNRS/Univ. Toulouse 1 (Documentation, Tests), 2010-2012
 * - Phan Huy Cuong, DREAM team, Univ. Can Tho (XText-based GAML), 2012
 * - Pierrick Koch, UMI 209 UMMISCO, IRD/UPMC (XText-based GAML), 2010-2011
 * - Romain Lavaud, UMI 209 UMMISCO, IRD/UPMC (RCP environment), 2010
 * - Francois Sempe, UMI 209 UMMISCO, IRD/UPMC (EMF model, Batch), 2007-2009
 * - Edouard Amouroux, UMI 209 UMMISCO, IRD/UPMC (C++ initial porting), 2007-2008
 * - Chu Thanh Quang, UMI 209 UMMISCO, IRD/UPMC (OpenMap integration), 2007-2008
 */
package msi.gaml.descriptions;

import java.io.File;
import java.util.*;
import msi.gama.common.interfaces.IGamlIssue;
import msi.gama.common.util.*;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.factories.IChildrenProvider;
import msi.gaml.statements.Facets;
import msi.gaml.types.*;
import org.eclipse.emf.common.notify.*;
import org.eclipse.emf.ecore.EObject;

/**
 * Written by drogoul Modified on 16 mai 2010
 * 
 * @todo Description
 * 
 */
public class ModelDescription extends SpeciesDescription {

	// TODO Move elsewhere
	public static ModelDescription ROOT;
	private final Map<String, ExperimentDescription> experiments = new LinkedHashMap();
	private final Map<String, ExperimentDescription> titledExperiments = new LinkedHashMap();
	private IDescription output;
	final TypesManager types;
	private String modelFilePath;
	private String modelFolderPath;
	private String modelProjectPath;
	private boolean isTorus = false;
	private final ErrorCollector collect = new ErrorCollector();

	public ModelDescription(String name, Class clazz, String projectPath, String modelPath, EObject source,
		SpeciesDescription macro, SpeciesDescription parent, Facets facets) {
		super(MODEL, clazz, macro, parent, IChildrenProvider.NONE, source, facets);
		types =
			new TypesManager(parent instanceof ModelDescription ? ((ModelDescription) parent).types
				: Types.builtInTypes);
		setModelFilePath(projectPath, modelPath);
		// number = count++;
	}

	public void setTorus(boolean b) {
		isTorus = b;
	}

	public boolean isTorus() {
		return isTorus;
	}

	@Override
	public String toString() {
		if ( modelFilePath.isEmpty() ) { return "abstract model"; }
		return "description of " + modelFilePath.substring(modelFilePath.lastIndexOf(File.separator));
	}

	@Override
	public void dispose() {
		if ( /* isDisposed || */isBuiltIn() ) { return; }
		experiments.clear();
		titledExperiments.clear();
		output = null;
		types.dispose();
		super.dispose();
		// isDisposed = true;
	}

	public String constructModelRelativePath(final String filePath, final boolean mustExist) {
		try {
			return FileUtils.constructAbsoluteFilePath(filePath, modelFilePath, mustExist);
		} catch (GamaRuntimeException e) {
			error(e.getMessage(), IGamlIssue.GENERAL);
			return filePath;
		}
	}

	/**
	 * @see org.eclipse.emf.common.notify.Adapter#notifyChanged(org.eclipse.emf.common.notify.Notification)
	 */
	@Override
	public void notifyChanged(final Notification notification) {}

	@Override
	public void unsetTarget(final Notifier object) {
		// Normally sent when the EObject is destroyed or no longer accepts the current description
		// as an adapter. In that case, whe should dispose the model description (the underlying
		// model has changed or been garbaged)
		// GuiUtils.debug("Removing: " + this + " from its EObject " + object);
		this.dispose();
	}

	/**
	 * Gets the model file name.
	 * 
	 * @return the model file name
	 */
	public String getModelFilePath() {
		return modelFilePath;
	}

	public String getModelFolderPath() {
		return modelFolderPath;
	}

	public String getModelProjectPath() {
		return modelProjectPath;
	}

	public void setModelFilePath(final String projectPath, final String filePath) {
		modelFilePath = filePath;
		modelFolderPath = new File("").getAbsoluteFile().getParent();
		modelProjectPath = projectPath;
	}

	/**
	 * Create types from the species descriptions
	 */
	public void buildTypes() {
		types.init();
	}

	public void addSpeciesType(final TypeDescription species) {
		// GuiUtils.debug("ModelDescription.addSpeciesType " + species.getName() + " in " + getName());
		types.addSpeciesType(species);
	}

	@Override
	public IDescription addChild(final IDescription child) {
		// GuiUtils.debug("Adding " + child + " to " + this + "...");
		if ( child instanceof ExperimentDescription ) {
			String s = child.getName();
			experiments.put(s, (ExperimentDescription) child);
			s = child.getFacets().getLabel(TITLE);
			titledExperiments.put(s, (ExperimentDescription) child);
			addSpeciesType((TypeDescription) child);
			// return child;
			// FIXME: Experiments are not disposed ?
			// If the experiment is not the "default" one, we return the child directly without
			// adding it to the children
			// if ( !DEFAULT_EXP.equals(s) ) { return child; }
			// FIXME Verify this
			children.add(child);
		} else if ( child != null && child.getKeyword().equals(OUTPUT) ) {
			if ( output == null ) {
				output = child;
			} else {
				output.addChildren(child.getChildren());
				return child;
			}
		} else {

			// GuiUtils.debug(" ..." + child + " added.");
			super.addChild(child);
			// else if ( child instanceof SpeciesDescription ) { // world_species
			// worldSpecies = (SpeciesDescription) child;
			// addSpeciesType(worldSpecies);
			// }
		}

		return child;
	}

	// @Override
	// public SpeciesDescription getWorldSpecies() {
	// return this;
	// // return worldSpecies;
	// }

	// @Override
	// protected boolean hasVar(final String name) {
	// return getWorldSpecies().hasVar(name);
	// }

	public boolean hasExperiment(final String name) {
		return experiments.containsKey(name) || titledExperiments.containsKey(name);
	}

	// @Override
	// public IDescription getDescriptionDeclaringVar(final String name) {
	// if ( hasVar(name) ) { return getWorldSpecies(); }
	// return null;
	// }

	// @Override
	// public IExpression getVarExpr(final String name) {
	// return getWorldSpecies().getVarExpr(name);
	// }
	//
	@Override
	public ModelDescription getModelDescription() {
		return this;
	}

	@Override
	public SpeciesDescription getSpeciesDescription(final String spec) {
		return (SpeciesDescription) types.getSpecies(spec);
	}

	public boolean hasSpeciesDescription(final String spec) {
		return types.containsSpecies(spec);
	}

	@Override
	public IType getTypeNamed(final String s) {
		return types.get(s);
	}

	public TypesManager getTypesManager() {
		return types;
	}

	@Override
	public SpeciesDescription getSpeciesContext() {
		return this;
	}

	public Set<String> getExperimentNames() {
		return new LinkedHashSet(experiments.keySet());
	}

	public Set<String> getExperimentTitles() {
		return new LinkedHashSet(titledExperiments.keySet());
	}

	@Override
	public IErrorCollector getErrorCollector() {
		return collect;
	}

	//
	// @Override
	// public boolean isAbstract() {
	// return this.getWorldSpecies().isAbstract();
	// }

	public ExperimentDescription getExperiment(String name) {
		ExperimentDescription desc = experiments.get(name);
		if ( desc == null ) {
			desc = titledExperiments.get(name);
		}
		return desc;
	}

	// @Override
	// protected void addPrimitive(StatementDescription action) {
	// GuiUtils.debug("ModelDescription.addPrimitive: " + action.getName() +
	// (action.isAbstract() ? " -- abstract " : ""));
	// super.addPrimitive(action);
	// }
	//
	// @Override
	// protected void addAction(StatementDescription action) {
	// GuiUtils
	// .debug("ModelDescription.addAction: " + action.getName() + (action.isAbstract() ? " -- abstract " : ""));
	// super.addAction(action);
	// }

	@Override
	public void finalizeDescription() {
		super.finalizeDescription();
		for ( StatementDescription action : actions.values() ) {
			if ( action.isAbstract() &&
				!action.getUnderlyingElement(null).eResource().equals(getUnderlyingElement(null).eResource()) ) {
				this.error("Abstract action '" + action.getName() + "', defined in " + action.getOriginName() +
					", should be redefined.", IGamlIssue.MISSING_ACTION);
			}
		}
	}

}
