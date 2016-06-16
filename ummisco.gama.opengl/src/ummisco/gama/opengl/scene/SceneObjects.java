/*********************************************************************************************
 *
 *
 * 'SceneObjects.java', in plugin 'msi.gama.jogl2', is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2014 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://code.google.com/p/gama-platform/ for license information and developers contact.
 *
 *
 **********************************************************************************************/
package ummisco.gama.opengl.scene;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3f;

import com.google.common.collect.Iterables;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.vividsolutions.jts.geom.Coordinate;

import msi.gama.metamodel.shape.IShape;
import msi.gaml.operators.fastmaths.FastMath;
import ummisco.gama.modernOpenGL.Maths;
import ummisco.gama.modernOpenGL.ShaderProgram;
import ummisco.gama.opengl.JOGLRenderer;
import ummisco.gama.opengl.camera.ICamera;

public class SceneObjects<T extends AbstractObject> implements ISceneObjects<T> {
	
	boolean isInit = false;
	ShaderProgram shaderProgram;
	ICamera camera;
	int[] vboHandles;
	static final int COLOR_IDX = 0;
	static final int VERTICES_IDX = 1;
	static final int IDX_BUFF_IDX = 2;
	
	ArrayList<ArrayList<float[]>> vbos = new ArrayList<ArrayList<float[]>>();
	
	private GL2 gl;
	
	private Matrix4f projectionMatrix;
	private Matrix4f transformationMatrix;
	private Matrix4f inverseYMatrix;

	public static class Static<T extends AbstractObject> extends SceneObjects<T> {

		Static(final ObjectDrawer<T> drawer) {
			super(drawer);
		}

		@Override
		public void add(final T object) {
			super.add(object);
			openGLListIndex = null;
		}

		@Override
		public void clear(final GL gl, final int traceSize, final boolean fading) {
		}
	}

	final ObjectDrawer<T> drawer;
	final LinkedList<List<T>> objects = new LinkedList();
	List<T> currentList;
	Integer openGLListIndex;
	boolean isFading;

	SceneObjects(final ObjectDrawer<T> drawer) {
		this.drawer = drawer;
		currentList = newCurrentList();
		objects.add(currentList);
	}

	private List newCurrentList() {
		return new CopyOnWriteArrayList();
	}

	@Override
	public void clear(final GL gl, final int sizeLimit, final boolean fading) {
		isFading = fading;

		final int size = objects.size();
		for (int i = 0, n = size - sizeLimit; i < n; i++) {
			final List<T> list = objects.poll();
			for (final T t : list) {
				t.dispose(gl);
			}
		}

		currentList = newCurrentList();
		objects.offer(currentList);
		final Integer index = openGLListIndex;
		if (index != null) {
			gl.getGL2().glDeleteLists(index, 1);
			openGLListIndex = null;
		}
	}

	@Override
	public void add(final T object) {
		currentList.add(object);
	}

	@Override
	public void remove(final T object) {
		currentList.remove(object);
	}

	@Override
	public Iterable<T> getObjects() {
		return Iterables.concat(objects);
	}

	private void drawPicking(final GL2 gl, final JOGLRenderer renderer) {
		gl.glPushMatrix();
		gl.glInitNames();
		gl.glPushName(0);
		double alpha = 0d;
		final int size = objects.size();
		final double delta = size == 0 ? 0 : 1d / size;
		for (final List<T> list : objects) {
			alpha = alpha + delta;
			for (final T object : list) {
				if (isFading) {
					final double originalAlpha = object.getAlpha();
					object.setAlpha(originalAlpha * alpha);
					object.draw(gl, drawer, true);
					object.setAlpha(originalAlpha);
				} else {
					object.draw(gl, drawer, true);
				}
			}
		}

		gl.glPopName();
		gl.glPopMatrix();

	}
	
	private void createProjectionMatrix() {
		
		final int height = drawer.getRenderer().getDrawable().getSurfaceHeight();
		final double aspect = (double) drawer.getRenderer().getDrawable().getSurfaceWidth() / (double) (height == 0 ? 1 : height);
		final double maxDim = drawer.getRenderer().getMaxEnvDim();
		final double zNear = maxDim / 1000;
		final double zFar = maxDim*10;
		final double frustum_length = zFar - zNear;
		double fW, fH;
		//final double fovY = 45.0d;
		final double fovY = drawer.getRenderer().data.getCameralens();
		if (aspect > 1.0) {
			fH = FastMath.tan(fovY / 360 * Math.PI) * zNear;
			fW = fH * aspect;
		} else {
			fW = FastMath.tan(fovY / 360 * Math.PI) * zNear;
			fH = fW / aspect;
		}
		
		projectionMatrix = new Matrix4f();
		
		projectionMatrix.m00 = (float) (zNear / fW);
		projectionMatrix.m11 = (float) (zNear / fH);
		projectionMatrix.m22 = (float) -((zFar + zNear) / frustum_length);
		projectionMatrix.m23 = -1;
		projectionMatrix.m32 = (float) -((2 * zNear * zFar) / frustum_length);
		projectionMatrix.m33 = 0;
	}
	
	public void initShader(final GL2 gl) {
		this.gl = gl;
		
		camera = drawer.getRenderer().camera;
		
		createProjectionMatrix();
		
		shaderProgram = new ShaderProgram(gl);
		shaderProgram.start();
		shaderProgram.loadProjectionMatrix(projectionMatrix);
		shaderProgram.stop();

		vboHandles = new int[3];
		this.gl.glGenBuffers(3, vboHandles, 0);
	}

	@Override
	public void draw(final GL2 gl, final boolean picking) {
		final JOGLRenderer renderer = drawer.getRenderer();
		if (objects.size() == 0) {
			return;
		}
		renderer.setCurrentColor(gl, Color.white);
		// GLUtilGLContext.SetCurrentColor(gl, 1.0f, 1.0f, 1.0f);
		if (picking) {
			drawPicking(gl, renderer);
			return;
		}
		
		if (drawer.getRenderer().data.isUseShader()) {
			drawWithShader(gl, picking);
		}
		else {
			drawWithoutShader(gl, picking);
		}
		
	}
	
	private void drawWithoutShader(final GL2 gl, final boolean picking) {
		Integer index = openGLListIndex;
		if (index == null) {
			index = gl.glGenLists(1);
			gl.glNewList(index, GL2.GL_COMPILE);
			double alpha = 0d;
			final int size = objects.size();
			final double delta = size == 0 ? 0 : 1d / size;
			for (final List<T> list : objects) {
				alpha = alpha + delta;
				for (final T object : list) {
					if (isFading) {
						final double originalAlpha = object.getAlpha();
						object.setAlpha(originalAlpha * alpha);
						object.draw(gl, drawer, picking);
						object.setAlpha(originalAlpha);
					} else {
						object.draw(gl, drawer, picking);
					}
				}
			}
			gl.glEndList();
		}
		gl.glCallList(index);
		openGLListIndex = index;
	}
	
	private void drawWithShader(final GL2 gl, final boolean picking) {
		
		if (!isInit) {
			initShader(gl);
//			isInit=true;
		}
		
		Integer index = openGLListIndex;
		if (index == null) {
			for (final List<T> list : objects) {
				for (final T object : list) {
					if (object instanceof GeometryObject) {
						GeometryObject geomObj = (GeometryObject)object;
						if (geomObj.getType() == IShape.Type.POLYGON || geomObj.getType() == IShape.Type.SPHERE) {
							ArrayList<float[]> vao = new ArrayList<float[]>();
							vao.add(getObjectVertices(object));
							vao.add(getObjectColors(object));
							vao.add(getObjectIndexBuffer(object));
							vbos.add(vao);
						}
					}
				}
			}
		}
		openGLListIndex = index;		
		
		if (vbos.size()>0)
		{
			// Clear screen
			gl.glClearColor(1, 0, 1, 0.5f);  // Purple
			gl.glClear(GL2.GL_STENCIL_BUFFER_BIT | GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT   );
		
			for (ArrayList<float[]> vbo : vbos) {
				float[] vtxPos = vbo.get(0);
				float[] vtxCol = vbo.get(1);
				float[] vtxIdxBuff = vbo.get(2);
				if (vtxPos == null || vtxCol == null) {
					int i = 5;
				}
				else if (vtxPos.length > 2)
					newDraw(vtxPos,vtxCol,vtxIdxBuff);
			}
		}
		
		vbos.clear();
	}
	
	private float[] getObjectVertices(AbstractObject object) {
		float[] result = null;
		if (object instanceof GeometryObject) {
			GeometryObject geomObj = (GeometryObject)object;
			final IShape.Type type = geomObj.getType();
			Coordinate[] coordsWithDoublons = geomObj.geometry.getCoordinates();
			Coordinate[] coords = Arrays.copyOf(coordsWithDoublons, coordsWithDoublons.length-1);
			
			result = new float[coords.length*3];
			for (int i = 0 ; i < coords.length ; i++) {
				result[3*i] = (float) coords[i].x;
				result[3*i+1] = (float) coords[i].y;
				result[3*i+2] = (float) coords[i].z;
			}
		}
		return result;
	}
	
	private float[] getObjectColors(AbstractObject object) {
		float[] result = null;
		if (object instanceof GeometryObject) {
			GeometryObject geomObj = (GeometryObject)object;
			final IShape.Type type = geomObj.getType();
			Coordinate[] coordsWithDoublons = geomObj.geometry.getCoordinates();
			Coordinate[] coords = Arrays.copyOf(coordsWithDoublons, coordsWithDoublons.length-1);

			float[] color = new float[]{ object.attributes.color.red(),
					object.attributes.color.green(), 
					object.attributes.color.blue(),
					object.attributes.color.alpha()};
			result = new float[coords.length*4];
			for (int i = 0 ; i < coords.length ; i++) {
				result[4*i] = (float) color[0];
				result[4*i+1] = (float) color[1];
				result[4*i+2] = (float) color[2];
				result[4*i+3] = (float) color[3];
			}
		}
		return result;
	}
	
	private float[] getObjectIndexBuffer(AbstractObject object) {
		// TODO : optimize this
		float[] result = null;
		if (object instanceof GeometryObject) {
			GeometryObject geomObj = (GeometryObject)object;
			final IShape.Type type = geomObj.getType();
			Coordinate[] coordsWithDoublons = geomObj.geometry.getCoordinates();
			Coordinate[] coords = Arrays.copyOf(coordsWithDoublons, coordsWithDoublons.length-1);
			
			int idx = 0;
			for (int i = 0 ; i < coords.length-2 ; i++) {
				for (int j = 0 ; j < coords.length-1 ; j++) {
					for (int k = 0 ; k < coords.length ; k++) {
						if (i != j && i != k && j != k) {
							idx+=3;
						}
					}
				}
			}
			result = new float[idx];
			idx = 0;
			for (int i = 0 ; i < coords.length-2 ; i++) {
				for (int j = 0 ; j < coords.length-1 ; j++) {
					for (int k = 0 ; k < coords.length ; k++) {
						if (i != j && i != k && j != k) {
							result[idx] = i;
							idx++;
							result[idx] = j;
							idx++;
							result[idx] = k;
							idx++;
						}
					}
				}
			}
		}
		return result;
	}
	
	private void newDraw(float[] vertices, float[] colors, float[] idxBuffer) {
//		idxBuffer = new float[]{0,1,2,0,2,3};
		shaderProgram.start();
		
		double angle = Math.toRadians( 30*( (int)(System.currentTimeMillis()/30 % 360) / 30) );		
		transformationMatrix = Maths.createTransformationMatrix(new Vector3f(0,0,0), 0, 0, 0, 1);
		shaderProgram.loadTransformationMatrix(transformationMatrix);
		shaderProgram.loadViewMatrix(camera);


		// VERTICES POSITIONS BUFFER
		// Observe that the vertex data passed to glVertexAttribPointer must stay valid
		// through the OpenGL rendering lifecycle.
		// Therefore it is mandatory to allocate a NIO Direct buffer that stays pinned in memory
		// and thus can not get moved by the java garbage collector.
		// Also we need to keep a reference to the NIO Direct buffer around up untill
		// we call glDisableVertexAttribArray first then will it be safe to garbage collect the memory.
		// I will here use the com.jogamp.common.nio.Buffers to quicly wrap the array in a Direct NIO buffer.
		FloatBuffer fbVertices = Buffers.newDirectFloatBuffer(vertices);
		// Select the VBO, GPU memory data, to use for vertices
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vboHandles[VERTICES_IDX]);
		// transfer data to VBO, this perform the copy of data from CPU -> GPU memory
		int numBytes = vertices.length * 4;
		gl.glBufferData(GL.GL_ARRAY_BUFFER, numBytes, fbVertices, GL.GL_STATIC_DRAW);
		fbVertices.rewind(); // It is OK to release CPU vertices memory after transfer to GPU
		// Associate Vertex attribute 0 with the last bound VBO
		gl.glVertexAttribPointer(0 /* the vertex attribute */, 3,
		                    GL2.GL_FLOAT, false /* normalized? */, 0 /* stride */,
		                    0 /* The bound VBO data offset */);
		gl.glEnableVertexAttribArray(0);
		
		// COLORS BUFFER
		FloatBuffer fbColors = Buffers.newDirectFloatBuffer(colors);
		// Select the VBO, GPU memory data, to use for colors
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vboHandles[COLOR_IDX]);
		numBytes = colors.length * 4;
		gl.glBufferData(GL2.GL_ARRAY_BUFFER, numBytes, fbColors, GL2.GL_STATIC_DRAW);
		fbColors.rewind(); // It is OK to release CPU color memory after transfer to GPU
		// Associate Vertex attribute 1 with the last bound VBO
		gl.glVertexAttribPointer(1 /* the vertex attribute */, 4 /* four positions used for each vertex */,
		                    GL2.GL_FLOAT, false /* normalized? */, 0 /* stride */,
		                    0 /* The bound VBO data offset */);
		gl.glEnableVertexAttribArray(1);
		
		// INDEX BUFFER
		int[] intIdxBuff = new int[idxBuffer.length];
		for (int i = 0; i < idxBuffer.length ; i++) {
			intIdxBuff[i] = (int) idxBuffer[i];
		}
		IntBuffer fbIdxBuff = Buffers.newDirectIntBuffer(intIdxBuff);
		// Select the VBO, GPU memory data, to use for colors
		gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, vboHandles[IDX_BUFF_IDX]);
		numBytes = colors.length * 4;
		gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, numBytes, fbIdxBuff, GL2.GL_STATIC_DRAW);
		fbIdxBuff.rewind();

//		gl.glDrawArrays(GL2.GL_TRIANGLES, 0, idxBuffer.length); //Draw the vertices as triangle
		gl.glDrawElements(GL2.GL_TRIANGLES, idxBuffer.length, GL2.GL_UNSIGNED_INT, 0);

		gl.glDisableVertexAttribArray(VERTICES_IDX); // Allow release of vertex position memory
		gl.glDisableVertexAttribArray(COLOR_IDX); // Allow release of vertex color memory
		
		shaderProgram.stop();
	}

	@Override
	public void preload(final GL2 gl) {
		final JOGLRenderer renderer = drawer.getRenderer();
		if (objects.size() == 0) {
			return;
		}
		for (final T object : objects.get(0)) {
			object.preload(gl, renderer);
		}
	}

}