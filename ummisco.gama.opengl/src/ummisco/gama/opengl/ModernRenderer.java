/*********************************************************************************************
 *
 * 'ModernRenderer.java, in plugin ummisco.gama.opengl, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.opengl;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.vecmath.Matrix4f;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

import msi.gama.common.interfaces.ILayer;
import msi.gama.metamodel.shape.Envelope3D;
import msi.gama.metamodel.shape.GamaPoint;
import msi.gama.metamodel.shape.IShape;
import msi.gama.outputs.layers.OverlayLayer;
import msi.gama.util.GamaColor;
import msi.gama.util.file.GamaFile;
import msi.gama.util.file.GamaGeometryFile;
import msi.gaml.statements.draw.FieldDrawingAttributes;
import msi.gaml.statements.draw.FileDrawingAttributes;
import msi.gaml.statements.draw.ShapeDrawingAttributes;
import msi.gaml.statements.draw.TextDrawingAttributes;
import msi.gaml.types.GamaGeometryType;
import ummisco.gama.modernOpenGL.ModernDrawer;
import ummisco.gama.opengl.scene.ModelScene;
import ummisco.gama.opengl.utils.GLUtilLight;
import ummisco.gama.opengl.vaoGenerator.TransformationMatrix;
import ummisco.gama.ui.utils.WorkbenchHelper;

/**
 * This class plays the role of Renderer and IGraphics. Class ModernRenderer.
 *
 * @author mazarsju
 * @since 23 avr. 2016
 *
 */
public class ModernRenderer extends Abstract3DRenderer {

	private Matrix4f projectionMatrix;

	private ModernDrawer drawer;
	public boolean renderToTexture = true;

	private final PickingState pickingState = new PickingState();
	public boolean colorPicking = false;
	private Envelope3D ROIEnvelope = null;
	private volatile boolean inited;

	protected static Map<String, Envelope> envelopes = new ConcurrentHashMap<>();
	protected final IntBuffer selectBuffer = Buffers.newDirectIntBuffer(1024);
	// Use to inverse y composant

	public ModernRenderer(final SWTOpenGLDisplaySurface d) {
		super(d);
		useShader = true;
	}

	@Override
	public void defineROI(final Point start, final Point end) {
		final GamaPoint startInWorld = getRealWorldPointFromWindowPoint(start);
		final GamaPoint endInWorld = getRealWorldPointFromWindowPoint(end);
		ROIEnvelope = new Envelope3D(new Envelope(startInWorld.x, endInWorld.x, startInWorld.y, endInWorld.y));
	}

	@Override
	public void cancelROI() {
		if (camera.isROISticky())
			return;
		ROIEnvelope = null;
	}

	@Override
	public void init(final GLAutoDrawable drawable) {

		WorkbenchHelper.run(() -> getCanvas().setVisible(visible));

		commonInit(drawable);

		setUpKeystoneCoordinates();

		drawer = new ModernDrawer(this, gl);

		GLUtilLight.InitializeLighting(gl, data, true);

		// We mark the renderer as inited
		inited = true;
	}

	private boolean visible;

	@Override
	public void display(final GLAutoDrawable drawable) {

		currentScene = sceneBuffer.getSceneToRender();
		if (currentScene == null) {
			return;
		}
		final GL2 gl = drawable.getContext().getGL().getGL2();
		// We preload any geometry, textures, etc. that are used in layers
		currentScene.preload(gl);

		if (renderToTexture)
			drawer.prepareFrameBufferObject();

		final Color background = data.getBackgroundColor();
		gl.glClearColor(background.getRed() / 255.0f, background.getGreen() / 255.0f, background.getBlue() / 255.0f,
				1.0f);
		gl.glClear(GL2.GL_STENCIL_BUFFER_BIT | GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

		gl.glClearDepth(1.0f);
		gl.glEnable(GL.GL_DEPTH_TEST); // enables depth testing
		gl.glDepthFunc(GL.GL_LEQUAL); // the type of depth test to do

		// TODO Is this line necessary ? The changes are made in init and
		// reshape
		updateCameraPosition();
		updatePerspective();

		this.rotateModel(gl);
		drawScene(gl);
		if (renderToTexture) {
			gl.glDisable(GL.GL_DEPTH_TEST); // disables depth testing
			drawer.renderToTexture();
		}

		if (ROIEnvelope != null) {
			drawROI(gl);
		}
		if (drawRotationHelper) {
			drawRotationHelper(gl);
		}
		if (!visible) {
			// We make the canvas visible only after a first display has occured
			visible = true;
			WorkbenchHelper.run(() -> getCanvas().setVisible(true));

		}

	}

	@Override
	public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int width,
			final int height) {
		// Get the OpenGL graphics context
		if (width <= 0 || height <= 0) {
			return;
		}
		updatePerspective();
	}

	@Override
	protected final void updatePerspective() {
		final int height = getDrawable().getSurfaceHeight();
		final int width = getDrawable().getSurfaceWidth();
		final double maxDim = getMaxEnvDim();
		final double fov = data.getCameralens();

		projectionMatrix = TransformationMatrix.createProjectionMatrix(data.isOrtho(), height, width, maxDim, fov);

		// shaderProgram.start();
		// shaderProgram.loadProjectionMatrix(projectionMatrix);
		// shaderProgram.loadViewMatrix(camera);
		// shaderProgram.stop();

		camera.animate();
	}

	public Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	public void drawScene(final GL2 gl) {
		currentScene = sceneBuffer.getSceneToRender();
		if (currentScene == null) {
			return;
		}
		// Do some garbage collecting in model scenes
		sceneBuffer.garbageCollect(gl);
		// if picking, we draw a first pass to pick the color
		if (pickingState.isBeginningPicking()) {
			beginPicking(gl);
			currentScene.draw(gl);
			endPicking(gl);
		}
		// we draw the scene on screen
		currentScene.draw(gl);
	}

	// Picking method
	// //////////////////////////////////////////////////////////////////////////////////////
	public void beginPicking(final GL2 gl) {
		// TODO
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	/**
	 * After drawing we have to calculate which object was nearest screen and
	 * return its index
	 * 
	 * @return name of selected object
	 */
	public void endPicking(final GL2 gl) {
		// TODO
	}

	@Override
	public void dispose(final GLAutoDrawable drawable) {
		// TODO
		sceneBuffer.garbageCollect((GL2) drawable.getGL());
		sceneBuffer.dispose();

		drawer.cleanUp();

		textureCache.dispose(drawable.getGL());
		geometryCache.dispose(drawable.getGL().getGL2());
		textRendererCache.dispose(drawable.getGL());
		this.canvas = null;
		this.camera = null;
		this.currentLayer = null;
		// this.setCurrentPickedObject(null);
		this.currentScene = null;
		drawable.removeGLEventListener(this);
	}

	@Override
	public void dispose() {
		super.dispose();
		dispose(getDrawable());
	}

	// Use when the rotation button is on.
	public void rotateModel(final GL2 gl) {
		if (data.isRotationOn()) {
			currentZRotation++;
		}
	}

	public ModernDrawer getDrawer() {
		return drawer;
	}

	@Override
	public Envelope3D getROIEnvelope() {
		return ROIEnvelope;
	}

	@Override
	public PickingState getPickingState() {
		return pickingState;
	}

	// This method is normally called either when the graphics is created or
	// when the output is changed
	// @Override
	@Override
	public void initScene() {
		if (sceneBuffer != null) {
			final ModelScene scene = sceneBuffer.getSceneToRender();
			if (scene != null) {
				scene.reload();
			}
		}
	}

	/**
	 * Method drawGeometry. Add a given JTS Geometry in the list of all the
	 * existing geometry that will be displayed by openGl.
	 */
	@Override
	public Rectangle2D drawShape(final IShape shape, final ShapeDrawingAttributes attributes) {
		if (shape == null) {
			return null;
		}
		if (sceneBuffer.getSceneToUpdate() == null) {
			return null;
		}
		// IShape.Type type = shape.getGeometricalType();
		if (highlight) {
			attributes.color = GamaColor.getInt(data.getHighlightColor().getRGB());
		}
		sceneBuffer.getSceneToUpdate().addGeometry(shape.getInnerGeometry(), attributes);

		return rect;

	}

	@Override
	public void startDrawRotationHelper(final GamaPoint pos) {
		rotationHelperPosition = pos;
		drawRotationHelper = true;
		final double distance = Math.sqrt(Math.pow(camera.getPosition().x - rotationHelperPosition.x, 2)
				+ Math.pow(camera.getPosition().y - rotationHelperPosition.y, 2)
				+ Math.pow(camera.getPosition().z - rotationHelperPosition.z, 2));
		final double size = distance / 15; // the size of the displayed axis
		if (currentScene != null)
			currentScene.startDrawRotationHelper(pos, size);
	}

	@Override
	public void stopDrawRotationHelper() {
		rotationHelperPosition = null;
		drawRotationHelper = false;
		if (currentScene != null)
			currentScene.stopDrawRotationHelper();
	}

	@Override
	public void startDrawKeystoneHelper() {
		drawKeystoneHelper = true;
		cornerSelected = -1;
	}

	@Override
	public void stopDrawKeystoneHelper() {
		drawKeystoneHelper = false;
	}

	/**
	 * Method drawImage.
	 *
	 * @param img
	 *            Image
	 * @param angle
	 *            Integer
	 */
	@Override
	public Rectangle2D drawImage(final BufferedImage img, final FileDrawingAttributes attributes) {
		if (sceneBuffer.getSceneToUpdate() == null) {
			return null;
		}
		if (attributes.size == null) {
			attributes.size = new GamaPoint(data.getEnvWidth(), data.getEnvHeight());
		}
		sceneBuffer.getSceneToUpdate().addImage(img, attributes);

		if (attributes.border != null) {
			drawGridLine(new GamaPoint(img.getWidth(), img.getHeight()), attributes.border);
		}
		return rect;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Rectangle2D drawFile(final GamaFile file, final FileDrawingAttributes attributes) {
		if (sceneBuffer.getSceneToUpdate() == null) {
			return null;
		}
		if (attributes.size == null) {
			attributes.size = new GamaPoint(data.getEnvWidth(), data.getEnvHeight());
		}

		if (file instanceof GamaGeometryFile && !envelopes.containsKey(file.getPath(surface.getScope()))) {
			envelopes.put(file.getPath(surface.getScope()), file.computeEnvelope(surface.getScope()));
		}
		sceneBuffer.getSceneToUpdate().addFile(file, attributes);
		return rect;
	}

	@Override
	public Rectangle2D drawField(final double[] fieldValues, final FieldDrawingAttributes attributes) {
		// TODO
		return null;
	}

	public void drawGridLine(final GamaPoint dimensions, final Color lineColor) {
		if (sceneBuffer.getSceneToUpdate() == null) {
			return;
		}
		double stepX, stepY;
		final double cellWidth = this.data.getEnvWidth() / dimensions.x;
		final double cellHeight = this.data.getEnvHeight() / dimensions.y;
		final GamaColor color = GamaColor.getInt(lineColor.getRGB());
		final ShapeDrawingAttributes attributes = new ShapeDrawingAttributes(null, color, color, IShape.Type.GRIDLINE);
		for (double i = 0; i < dimensions.x; i++) {
			for (double j = 0; j < dimensions.y; j++) {
				stepX = i + 0.5;
				stepY = j + 0.5;
				final Geometry g = GamaGeometryType
						.buildRectangle(cellWidth, cellHeight, new GamaPoint(stepX * cellWidth, stepY * cellHeight))
						.getInnerGeometry();
				sceneBuffer.getSceneToUpdate().addGeometry(g, attributes);
			}
		}
	}

	@Override
	public Rectangle2D drawString(final String string, final TextDrawingAttributes attributes) {
		// Multiline: Issue #780
		if (sceneBuffer.getSceneToUpdate() == null) {
			return null;
		}
		if (string.contains("\n")) {
			for (final String s : string.split("\n")) {
				attributes.location.setY(attributes.location.getY()
						+ attributes.font.getSize() * this.getyRatioBetweenPixelsAndModelUnits());
				drawString(s, attributes);
			}
			return null;
		}
		attributes.location.setY(-attributes.location.getY());
		sceneBuffer.getSceneToUpdate().addString(string, attributes);
		return null;
	}

	@Override
	public void fillBackground(final Color bgColor, final double opacity) {
		setOpacity(opacity);
	}

	/**
	 * Each new step the Z value of the first layer is set to 0.
	 */
	@Override
	public boolean beginDrawingLayers() {
		while (!inited) {
			try {
				Thread.sleep(10);
			} catch (final InterruptedException e) {
				return false;
			}
		}
		return sceneBuffer.beginUpdatingScene();

	}

	/**
	 * Set the value z of the current Layer. If no value is define is defined
	 * set it to 0. Set the type of the layer weather it's a static layer
	 * (refresh:false) or a dynamic layer (by default or refresh:true)
	 */
	@Override
	public void beginDrawingLayer(final ILayer layer) {
		super.beginDrawingLayer(layer);
		GamaPoint currentOffset, currentScale;
		if (!(layer instanceof OverlayLayer)) {
			final double currentZLayer = getMaxEnvDim() * layer.getPosition().getZ();

			// get the value of the z scale if positive otherwise set it to 1.
			double z_scale;
			if (layer.getExtent().getZ() > 0) {
				z_scale = layer.getExtent().getZ();
			} else {
				z_scale = 1;
			}

			currentOffset = new GamaPoint(getXOffsetInPixels() / (getWidth() / data.getEnvWidth()),
					getYOffsetInPixels() / (getHeight() / data.getEnvHeight()), currentZLayer);
			currentScale = new GamaPoint(getLayerWidth() / getWidth(), getLayerHeight() / getHeight(), z_scale);
		} else {
			currentOffset = new GamaPoint(getXOffsetInPixels(), getYOffsetInPixels());
			currentScale = new GamaPoint(1, 1, 1);
		}
		final ModelScene scene = sceneBuffer.getSceneToUpdate();
		if (scene != null) {
			scene.beginDrawingLayer(layer, currentOffset, currentScale, currentAlpha);
		}
	}

	/**
	 * Method endDrawingLayers()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#endDrawingLayers()
	 */
	@Override
	public void endDrawingLayers() {
		sceneBuffer.endUpdatingScene();
		getSurface().invalidateVisibleRegions();
	}

	@Override
	public GamaPoint getRealWorldPointFromWindowPoint(final Point windowPoint) {
		// TODO
		return null;
	}

	/**
	 * Method beginOverlay()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#beginOverlay(msi.gama.outputs.layers.OverlayLayer)
	 */
	@Override
	public void beginOverlay(final OverlayLayer layer) {
		// TODO
	}

	/**
	 * Method endOverlay()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#endOverlay()
	 */
	@Override
	public void endOverlay() {
		// TODO
	}

	@Override
	public boolean mouseInROI(final Point mousePosition) {
		// TODO
		return false;
	}

	@Override
	public boolean cannotDraw() {
		return sceneBuffer.getSceneToUpdate() != null && sceneBuffer.getSceneToUpdate().cannotAdd();
	}

	@Override
	public void drawROI(final GL2 gl) {
		// TODO

	}

	@Override
	public void drawRotationHelper(final GL2 gl) {
		// TODO

	}

	@Override
	public Integer getGeometryListFor(final GL2 gl, final GamaGeometryFile file) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TextRenderer getTextRendererFor(final Font font) {
		// TODO Auto-generated method stub
		return null;
	}

}
