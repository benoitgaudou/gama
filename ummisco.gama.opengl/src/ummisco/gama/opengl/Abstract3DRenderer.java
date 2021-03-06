/*********************************************************************************************
 *
 * 'Abstract3DRenderer.java, in plugin ummisco.gama.opengl, is part of the source code of the GAMA modeling and
 * simulation platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.opengl;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.swt.GLCanvas;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.vividsolutions.jts.geom.Envelope;

import msi.gama.common.GamaPreferences;
import msi.gama.metamodel.shape.Envelope3D;
import msi.gama.metamodel.shape.GamaPoint;
import msi.gama.metamodel.shape.ILocation;
import msi.gama.outputs.display.AbstractDisplayGraphics;
import msi.gama.util.file.GamaGeometryFile;
import msi.gaml.operators.fastmaths.FastMath;
import ummisco.gama.opengl.camera.CameraArcBall;
import ummisco.gama.opengl.camera.FreeFlyCamera;
import ummisco.gama.opengl.camera.ICamera;
import ummisco.gama.opengl.scene.AbstractObject;
import ummisco.gama.opengl.scene.ModelScene;
import ummisco.gama.opengl.scene.ObjectDrawer;
import ummisco.gama.opengl.scene.SceneBuffer;
import ummisco.gama.opengl.vaoGenerator.DrawingEntityGenerator;
import ummisco.gama.opengl.vaoGenerator.ShapeCache;
import ummisco.gama.ui.utils.WorkbenchHelper;

/**
 * This class plays the role of Renderer and IGraphics. Class Abstract3DRenderer.
 *
 * @author drogoul
 * @since 27 avr. 2015
 *
 */
public abstract class Abstract3DRenderer extends AbstractDisplayGraphics implements GLEventListener {

	public class PickingState {

		final static int NONE = -2;
		final static int WORLD = -1;

		volatile boolean isPicking;
		volatile boolean isMenuOn;
		volatile int pickedIndex = NONE;

		public void setPicking(final boolean isPicking) {
			this.isPicking = isPicking;
			// System.out.println(
			// "Picking is " + isPicking + " with menu on: " + isMenuOn + " and
			// picked index " + pickedIndex);
			if (!isPicking) {
				setPickedIndex(NONE);
				setMenuOn(false);
			}
		}

		public void setMenuOn(final boolean isMenuOn) {
			// System.out.println("Menu on is " + isMenuOn);
			this.isMenuOn = isMenuOn;
		}

		public void setPickedIndex(final int pickedIndex) {
			this.pickedIndex = pickedIndex;
			// System.out.println("Picked object = " + pickedIndex);
			if (pickedIndex == WORLD && !isMenuOn) {
				// Selection occured, but no object have been selected
				setMenuOn(true);
				getSurface().selectAgent(null);
			}
		}

		public boolean isPicked(final int objectIndex) {
			return pickedIndex == objectIndex;
		}

		public boolean isBeginningPicking() {
			return isPicking && pickedIndex == NONE;
		}

		public boolean isMenuOn() {
			return isMenuOn;
		}

		public boolean isPicking() {
			return isPicking;
		}

	}

	public final static int Y_FLAG = -1;

	private Color currentColor;
	protected DrawingEntityGenerator drawingEntityGenerator;

	protected boolean useShader = false;

	public SceneBuffer sceneBuffer;
	protected ModelScene currentScene;
	protected GLCanvas canvas;
	public ICamera camera;
	protected double currentZRotation = 0;
	int[] viewport = new int[4];
	double mvmatrix[] = new double[16];
	double projmatrix[] = new double[16];
	public boolean colorPicking = false;
	protected GLU glu;
	protected GL2 gl;
	// relative to rotation helper
	protected boolean drawRotationHelper = false;
	protected GamaPoint rotationHelperPosition = null;
	// relative to keystone
	protected boolean drawKeystoneHelper = false;

	public boolean drawKeystoneHelper() {
		return drawKeystoneHelper;
	}

	protected float[][] keystoneCoordinates;

	public float[][] getKeystoneCoordinates() {
		return keystoneCoordinates;
	}

	public void setKeystoneCoordinates(final int cornerId, final float[] coordinates) {
		keystoneCoordinates[cornerId] = coordinates;
	}

	protected int cornerSelected = -1;

	public int getCornerSelected() {
		return cornerSelected;
	}

	protected final GeometryCache geometryCache = new GeometryCache();
	protected final TextRenderersCache textRendererCache = new TextRenderersCache();
	protected final TextureCache textureCache =
			GamaPreferences.DISPLAY_SHARED_CONTEXT.getValue() ? TextureCache.getSharedInstance() : new TextureCache();

	public static Boolean isNonPowerOf2TexturesAvailable = false;
	protected static Map<String, Envelope> envelopes = new ConcurrentHashMap<>();
	protected final IntBuffer selectBuffer = Buffers.newDirectIntBuffer(1024);

	public Abstract3DRenderer(final SWTOpenGLDisplaySurface d) {
		super(d);
		camera = new CameraArcBall(this);
		sceneBuffer = new SceneBuffer(this);
		ShapeCache.freedShapeCache();
	}

	@SuppressWarnings ("unused")
	public GLAutoDrawable createDrawable(final Composite parent) {
		final boolean useSharedContext = GamaPreferences.DISPLAY_SHARED_CONTEXT.getValue();
		final GLProfile profile =
				useSharedContext ? TextureCache.getSharedContext().getGLProfile() : GLProfile.getDefault();
		final GLCapabilities cap = new GLCapabilities(profile);
		cap.setStencilBits(8);
		cap.setDoubleBuffered(true);
		cap.setHardwareAccelerated(true);
		cap.setSampleBuffers(true);
		cap.setAlphaBits(4);
		cap.setNumSamples(4);
		canvas = new GLCanvas(parent, SWT.NONE, cap, null);
		if (useSharedContext) {
			canvas.setSharedAutoDrawable(TextureCache.getSharedContext());
		}
		canvas.setAutoSwapBufferMode(true);
		new SWTGLAnimator(canvas);
		canvas.addGLEventListener(this);
		final FillLayout gl = new FillLayout();
		canvas.setLayout(gl);
		return canvas;
	}

	protected void commonInit(final GLAutoDrawable drawable) {
		// the drawingEntityGenerator is used only when there is a webgl display
		// and/or a modernRenderer.
		drawingEntityGenerator = new DrawingEntityGenerator(this);

		glu = new GLU();
		currentZRotation = data.getZRotation();
		gl = drawable.getContext().getGL().getGL2();
		final Color background = data.getBackgroundColor();
		gl.glClearColor(background.getRed() / 255.0f, background.getGreen() / 255.0f, background.getBlue() / 255.0f,
				1.0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
		isNonPowerOf2TexturesAvailable = gl.isNPOTTextureAvailable();

		initializeCanvasListeners();
		updateCameraPosition();
		updatePerspective();
	}

	public abstract void initScene();

	public abstract PickingState getPickingState();

	public final ModelScene getCurrentScene() {
		return currentScene;
	}

	public abstract Integer getGeometryListFor(final GL2 gl, final GamaGeometryFile file);

	public abstract TextRenderer getTextRendererFor(final Font font);

	public abstract void defineROI(final Point start, final Point end);

	public abstract void cancelROI();

	public final GLCanvas getCanvas() {
		return canvas;
	}

	public final GLAutoDrawable getDrawable() {
		return canvas;
	}

	protected void initializeCanvasListeners() {

		WorkbenchHelper.asyncRun(() -> {
			if (getCanvas() == null || getCanvas().isDisposed()) { return; }
			getCanvas().addKeyListener(camera);
			getCanvas().addMouseListener(camera);
			getCanvas().addMouseMoveListener(camera);
			getCanvas().addMouseWheelListener(camera);
			getCanvas().addMouseTrackListener(camera);

		});

	}

	public final boolean getDrawNormal() {
		return data.isDraw_norm();
	}

	public final double getMaxEnvDim() {
		// built dynamically to prepare for the changes in size of the
		// environment
		final double env_width = data.getEnvWidth();
		final double env_height = data.getEnvHeight();
		return env_width > env_height ? env_width : env_height;
	}

	public final double getEnvWidth() {
		return data.getEnvWidth();
	}

	public final double getEnvHeight() {
		return data.getEnvHeight();
	}

	// public GL2 getContext() {
	// return gl;
	// }

	public DrawingEntityGenerator getDrawingEntityGenerator() {
		return drawingEntityGenerator;
	}

	public final void switchCamera() {
		final ICamera oldCamera = camera;
		WorkbenchHelper.asyncRun(() -> {
			getCanvas().removeKeyListener(oldCamera);
			getCanvas().removeMouseListener(oldCamera);
			getCanvas().removeMouseMoveListener(oldCamera);
			getCanvas().removeMouseWheelListener(oldCamera);
			getCanvas().removeMouseTrackListener(oldCamera);
		});

		if (!data.isArcBallCamera()) {
			camera = new FreeFlyCamera(this);
		} else {
			camera = new CameraArcBall(this);
		}

		initializeCanvasListeners();

	}

	public final double getWidth() {
		return getDrawable().getSurfaceWidth() * surface.getZoomLevel();
	}

	public final double getHeight() {
		return getDrawable().getSurfaceHeight() * surface.getZoomLevel();
	}

	public final void updateCameraPosition() {
		camera.update();
	}

	protected abstract void updatePerspective();

	public abstract void drawROI(final GL2 gl);

	public abstract Envelope3D getROIEnvelope();

	public abstract void startDrawRotationHelper(final GamaPoint pos);

	public abstract void stopDrawRotationHelper();

	public abstract void startDrawKeystoneHelper();

	public abstract void stopDrawKeystoneHelper();

	public abstract void drawRotationHelper(final GL2 gl);

	@Override
	public void fillBackground(final Color bgColor, final double opacity) {
		setOpacity(opacity);
	}

	/**
	 * Method getDisplayWidthInPixels()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#getDisplayWidthInPixels()
	 */
	@Override
	public final int getDisplayWidth() {
		return (int) FastMath.round(getWidth());
	}

	/**
	 * Method getDisplayHeightInPixels()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#getDisplayHeightInPixels()
	 */
	@Override
	public final int getDisplayHeight() {
		return (int) FastMath.round(getHeight());
	}

	/**
	 * Method setOpacity()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#setOpacity(double)
	 */
	@Override
	public final void setOpacity(final double alpha) {
		currentAlpha = alpha;
	}

	public final GLU getGlu() {
		return glu;
	}

	public final GamaPoint getIntWorldPointFromWindowPoint(final Point windowPoint) {
		final GamaPoint p = getRealWorldPointFromWindowPoint(windowPoint);
		return new GamaPoint((int) p.x, (int) p.y);
	}

	public abstract GamaPoint getRealWorldPointFromWindowPoint(final Point windowPoint);

	/**
	 * Method getZoomLevel()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#getZoomLevel()
	 */
	@Override
	public final Double getZoomLevel() {
		return data.getZoomLevel();
	}

	/**
	 * Useful for drawing fonts
	 * 
	 * @return
	 */
	public final double getGlobalYRatioBetweenPixelsAndModelUnits() {
		return getHeight() / data.getEnvHeight();
	}

	/**
	 * Method is2D()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#is2D()
	 */
	@Override
	public final boolean is2D() {
		return false;
	}

	/**
	 * @param path
	 * @return
	 */
	public final static Envelope getEnvelopeFor(final String path) {
		return envelopes.get(path);
	}

	/**
	 * @return
	 */
	public static float getLineWidth() {
		return GamaPreferences.CORE_LINE_WIDTH.getValue().floatValue();
	}

	@Override
	public final SWTOpenGLDisplaySurface getSurface() {
		return (SWTOpenGLDisplaySurface) surface;
	}

	@Override
	public final ILocation getCameraPos() {
		return camera.getPosition();
	}

	@Override
	public final ILocation getCameraTarget() {
		return camera.getTarget();
	}

	@Override
	public final ILocation getCameraOrientation() {
		return camera.getOrientation();
	}

	public final boolean useShader() {
		return useShader;
	}

	public TextureCache getSharedTextureCache() {
		return textureCache;
	}

	public abstract boolean mouseInROI(final Point mousePosition);

	// TODO : maybe those following functions are to put anywhere else...
	public void setCurrentColor(final GL2 gl, final Color c, final double alpha) {
		if (c == null)
			return;
		setCurrentColor(gl, c.getRed() / 255d, c.getGreen() / 255d, c.getBlue() / 255d, c.getAlpha() / 255d * alpha);
	}

	public void setCurrentColor(final GL2 gl, final Color c) {
		setCurrentColor(gl, c, 1);
	}

	public void setCurrentColor(final GL2 gl, final double red, final double green, final double blue,
			final double alpha) {
		currentColor = new Color((float) red, (float) green, (float) blue, (float) alpha);
		gl.glColor4d(red, green, blue, alpha);
	}

	public void setCurrentColor(final GL2 gl, final double value) {
		setCurrentColor(gl, value, value, value, 1);
	}

	public Color getCurrentColor() {
		return currentColor;
	}

	@SuppressWarnings ("rawtypes")
	public ObjectDrawer getDrawerFor(final Class<? extends AbstractObject> class1) {
		return null;
	}

	public double getZRotation() {
		return currentZRotation;
	}

	public boolean isDrawRotationHelper() {
		return drawRotationHelper;
	}

	public GamaPoint getRotationHelperPosition() {
		return rotationHelperPosition;
	}

	public void setUpKeystoneCoordinates() {
		keystoneCoordinates = new float[4][2];
		float[] coords1 = new float[] { 0, 1 }; // bottom-left
		float[] coords2 = new float[] { 0, 0 }; // top-left
		float[] coords3 = new float[] { 1, 0 }; // top-right
		float[] coords4 = new float[] { 1, 1 }; // bottom-right
		if (data.getKeystone() != null) {
			coords1 =
					new float[] { (float) data.getKeystone().get(2).getX(), (float) data.getKeystone().get(2).getY() };
			coords2 =
					new float[] { (float) data.getKeystone().get(0).getX(), (float) data.getKeystone().get(0).getY() };
			coords3 =
					new float[] { (float) data.getKeystone().get(1).getX(), (float) data.getKeystone().get(1).getY() };
			coords4 =
					new float[] { (float) data.getKeystone().get(3).getX(), (float) data.getKeystone().get(3).getY() };
		}
		setKeystoneCoordinates(0, coords1);
		setKeystoneCoordinates(1, coords2);
		setKeystoneCoordinates(2, coords3);
		setKeystoneCoordinates(3, coords4);
	}

	public void cornerSelected(final int cornerId) {
		cornerSelected = cornerId;
	}

}
