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
package msi.gama.outputs.layers;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import msi.gama.common.interfaces.*;
import msi.gama.common.util.ImageUtils;
import msi.gama.precompiler.GamlAnnotations.facet;
import msi.gama.precompiler.GamlAnnotations.facets;
import msi.gama.precompiler.GamlAnnotations.inside;
import msi.gama.precompiler.GamlAnnotations.symbol;
import msi.gama.precompiler.*;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.descriptions.IDescription;
import msi.gaml.types.IType;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Written by drogoul Modified on 9 nov. 2009
 * 
 * @todo Description
 * 
 */
@symbol(name = IKeyword.QUADTREE, kind = ISymbolKind.LAYER, with_sequence = false)
@inside(symbols = IKeyword.DISPLAY)
@facets(value = { @facet(name = IKeyword.POSITION, type = IType.POINT, optional = true),
	@facet(name = IKeyword.SIZE, type = IType.POINT, optional = true),
	@facet(name = IKeyword.TRANSPARENCY, type = IType.FLOAT, optional = true),
	@facet(name = IKeyword.NAME, type = IType.LABEL, optional = false),
	@facet(name = IKeyword.Z, type = IType.FLOAT, optional = true),
	@facet(name = IKeyword.REFRESH, type = IType.BOOL, optional = true) }, omissible = IKeyword.NAME)
public class QuadTreeLayerStatement extends AbstractLayerStatement {

	BufferedImage supportImage;

	// private IEnvironment modelEnv;

	public QuadTreeLayerStatement(/* final ISymbol context, */final IDescription desc) throws GamaRuntimeException {
		super(desc);
	}

	@Override
	public void _init(final IScope scope) throws GamaRuntimeException {
		Envelope env = scope.getSimulationScope().getEnvelope();
		supportImage = ImageUtils.createCompatibleImage((int) env.getWidth(), (int) env.getHeight());
	}

	@Override
	public void _step(final IScope scope) throws GamaRuntimeException {
		IGraphics g = scope.getGraphics();
		if ( g != null ) {
			if ( supportImage.getWidth() != g.getDisplayWidthInPixels() ||
				supportImage.getHeight() != g.getDisplayHeightInPixels() ) {
				supportImage.flush();
				supportImage =
					ImageUtils.createCompatibleImage(g.getDisplayWidthInPixels(), g.getDisplayHeightInPixels());
			}
		}
		Graphics2D g2 = (Graphics2D) supportImage.getGraphics();
		scope.getTopology().displaySpatialIndexOn(g2, supportImage.getWidth(), supportImage.getHeight());
	}

	@Override
	public short getType() {
		return ILayerStatement.QUADTREE;
	}

	@Override
	public void dispose() {
		supportImage.flush();
		supportImage = null;
		super.dispose();
	}

	public BufferedImage getSupportImage() {
		return supportImage;
	}
}
