package msi.gama.metamodel.agent;

import com.vividsolutions.jts.geom.Envelope;

import msi.gama.common.interfaces.IKeyword;
import msi.gama.kernel.simulation.ISimulation;
import msi.gama.metamodel.population.*;
import msi.gama.metamodel.shape.*;
import msi.gama.metamodel.topology.IEnvironment;
import msi.gama.precompiler.GamlAnnotations.species;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.compilation.AbstractGamlAdditions;
import msi.gaml.operators.Spatial.Transformations;
import msi.gaml.species.ISpecies;
import msi.gaml.types.GamaGeometryType;

@species(name = IKeyword.WORLD_SPECIES)
public class WorldAgent extends GamlAgent {

	private GamaPoint location;
	private boolean isTorus;

	public WorldAgent(final ISimulation sim, final IPopulation s) throws GamaRuntimeException {
		super(sim, s);
		index = 0;
	}

	@Override
	public synchronized GamaPoint getLocation() {
		return location;
	}

	@Override
	public synchronized IShape getGeometry() {
		return geometry;
	}

	@Override
	public synchronized void setLocation(final ILocation newGlobalLoc) {}

	@Override
	public synchronized void setGeometry(final IShape newGlobalGeometry) {
		if (geometry != null) {
			IScope scope = this.getSimulation().getGlobalScope();
			IEnvironment modelEnv = scope.getSimulationScope().getModel().getModelEnvironment();
			Envelope env = newGlobalGeometry.getEnvelope();
			GamaPoint p = new GamaPoint(-1 * env.getMinX(), - 1 * env.getMinY());
			geometry = Transformations.primTranslationBy(newGlobalGeometry, p);
			modelEnv.initializeFor(newGlobalGeometry, scope);
		}
	}

	public void initializeLocationAndGeomtry(final IScope scope) {
		IEnvironment modelEnv = scope.getSimulationScope().getModel().getModelEnvironment();
		double width = modelEnv.getWidth();
		double height = modelEnv.getHeight();
		isTorus = modelEnv.isTorus();
		location = new GamaPoint(width / 2, height / 2);
		geometry = GamaGeometryType.buildRectangle(width, height, location);
	}

	@Override
	// Special case for built-in species handled by the world (and not created before)
	public IPopulation getPopulationFor(final String speciesName) throws GamaRuntimeException {
		IPopulation pop = super.getPopulationFor(speciesName);

		if ( pop != null ) { return pop; }

		if ( AbstractGamlAdditions.isBuiltIn(speciesName) ) {
			ISpecies microSpec = this.getSpecies().getMicroSpecies(speciesName);
			pop = new GamlPopulation(this, microSpec);
			microPopulations.put(microSpec, pop);
			pop.initializeFor(this.getSimulation().getExecutionScope());
			return pop;
		}

		return null;
	}

	public void setTorus(final boolean isTorus) {
		this.isTorus = isTorus;
	}

	@Override
	public boolean isTorus() {
		return isTorus;
	}

}