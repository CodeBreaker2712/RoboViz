/*
 *  Copyright 2011 Justin Stoecker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package jsgl.jogl.model;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

import java.io.*;
import java.util.ArrayList;
import jsgl.jogl.GLDisposable;
import jsgl.math.BoundingBox;
import jsgl.math.vector.Vec3f;

/**
 * A model loaded from a Wavefront OBJ file.<br>
 * Supports the following vertex data:<br>
 * -Geometric vertices (v)<br>
 * -Texture vertices (vt)<br>
 * -Vertex normals (vn)<br>
 * <br>
 * Supported the following elements:<br>
 * -Points (p)<br>
 * -Lines (p)<br>
 * -Faces (f)<br>
 * <br>
 * Supported grouping types:<br>
 * -Group name (g)<br>
 * -Object name(o) <br>
 * -Smoothing group (s)<br>
 *
 * @author Justin Stoecker
 */
public class ObjModel implements GLDisposable
{
	/**
	 * Contains indexing information for vertex positions, normals, and texture
	 * coordinates. The indices stored in these objects are 0-indexed, but the
	 * input string should be 1-indexed (according to OBJ format).
	 */
	public static class Face
	{
		int[] vertIndices = null;
		int[] normalIndices = null;
		int[] texCoordIndices = null;
		ObjMaterial material;

		public Face(String line, ObjMaterial material)
		{
			String[] parts = line.split("\\s+");
			vertIndices = new int[parts.length - 1];
			this.material = material;

			String[] subparts = parts[1].split("/");
			if (subparts.length == 1) {
				readVertexFormat(parts);
			} else if (subparts.length == 2) {
				readVertexTexCoordsFormat(parts);
			} else if (subparts[1].isEmpty()) {
				readVertexNormalFormat(parts);
			} else {
				readVertexTexCoordsNormalFormat(parts);
			}
		}

		private void readVertexFormat(String[] parts)
		{
			// format: f v v v ...
			// where v = vertex index

			for (int i = 0; i < vertIndices.length; i++) {
				vertIndices[i] = Integer.parseInt(parts[i + 1]) - 1;
			}
		}

		private void readVertexNormalFormat(String[] parts)
		{
			// format: f v//vn v//vn v//vn ...
			// where v = vertex index, vn = normal index

			normalIndices = new int[vertIndices.length];
			for (int i = 0; i < vertIndices.length; i++) {
				String[] subparts = parts[i + 1].split("/");
				vertIndices[i] = Integer.parseInt(subparts[0]) - 1;
				normalIndices[i] = Integer.parseInt(subparts[2]) - 1;
			}
		}

		private void readVertexTexCoordsFormat(String[] parts)
		{
			// format: f v/vt v/vt v/vt ...
			// where v = vertex index, vt = normal index

			texCoordIndices = new int[vertIndices.length];
			for (int i = 0; i < vertIndices.length; i++) {
				String[] subparts = parts[i + 1].split("/");
				vertIndices[i] = Integer.parseInt(subparts[0]) - 1;
				texCoordIndices[i] = Integer.parseInt(subparts[1]) - 1;
			}
		}

		private void readVertexTexCoordsNormalFormat(String[] parts)
		{
			// format: f v/vt/vn v/vt/vn v/vt/vn ...
			// where v = vertex index, vt = normal index, vn = normal index

			texCoordIndices = new int[vertIndices.length];
			normalIndices = new int[vertIndices.length];
			for (int i = 0; i < vertIndices.length; i++) {
				String[] subparts = parts[i + 1].split("/");
				vertIndices[i] = Integer.parseInt(subparts[0]) - 1;
				texCoordIndices[i] = Integer.parseInt(subparts[1]) - 1;
				normalIndices[i] = Integer.parseInt(subparts[2]) - 1;
			}
		}
	}

	private ArrayList<ObjGroup> groups = new ArrayList<>();
	private BoundingBox bounds;
	private ArrayList<float[]> verts = new ArrayList<>();
	private ArrayList<float[]> normals = new ArrayList<>();
	private ArrayList<float[]> texCoords = new ArrayList<>();
	private ObjMaterialLibrary mtllib = null;
	private boolean disposed = false;

	public BoundingBox getBounds()
	{
		return bounds;
	}

	private ObjModel()
	{
	}

	/**
	 * Reads file and stores it in an OBJ format that maintains the groupings
	 * specified in the file. The render mode for this is not optimized, and the
	 * preferred method is loading as a mesh. This is left for debug purposes.
	 */
	public static ObjModel load(File file) throws IOException 
	{
		ObjModel model = new ObjModel();
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line;

		Vec3f min = new Vec3f(Float.POSITIVE_INFINITY);
		Vec3f max = new Vec3f(Float.NEGATIVE_INFINITY);

		ObjGroup currentGroup = null;
		ObjMaterial currentMaterial = new ObjMaterial("Default Material");

		while ((line = br.readLine()) != null) {
			line = line.trim();
			LineType lineType = getLineType(line);
			switch (lineType) {
				case VERTEX:
					processVertexLine(line, model, min, max);
					break;
				case NORMAL:
					model.normals.add(readFloatValues(line));
					break;
				case TEX_COORD:
					model.texCoords.add(readFloatValues(line));
					break;
				case FACE:
					processFaceLine(line, currentGroup, currentMaterial, model);
					break;
				case MATERIAL_LIBRARY:
					processMaterialLibrary(line, file, model);
					break;
				case USE_MATERIAL:
					processUseMaterial(line, model);
					break;
				case GROUP:
					currentGroup = processGroup(line, model, currentGroup);
					break;
			}
		}
		br.close();

		if (!model.groups.contains(currentGroup))
			model.groups.add(currentGroup);
		model.bounds = new BoundingBox(min, max);

		return model;
	}

	private static ObjGroup processGroup(String line, ObjModel model, ObjGroup currentGroup) {
		if (currentGroup != null) model.groups.add(currentGroup);
		return new ObjGroup(line.split("\\s+")[1]);
	}


	private static void processUseMaterial(String line, ObjModel model) {
		String requestedMaterial = line.split("\\s+")[1];
		ObjMaterial currentMaterial;
		for (ObjMaterial mat : model.mtllib.materials)
			if (mat.name.equals(requestedMaterial))
				currentMaterial = mat;
	}

	private static void processMaterialLibrary(String line, File file, ObjModel model) throws IOException {
		File f = new File(file.getParent(), line.split("\\s+")[1]);
		if (model.mtllib == null) model.mtllib = new ObjMaterialLibrary();
		BufferedReader br2 = new BufferedReader(new FileReader(f));
		file.getParentFile();
		model.mtllib.load(br2, file.getParent(), null);
		// TODO: can files have multiple material libraries?
	}


	private static void processFaceLine(String line, ObjGroup currentGroup, ObjMaterial currentMaterial, ObjModel model) {
		if (currentGroup == null) currentGroup = new ObjGroup("Unnamed Default Group");
		currentGroup.faces.add(new Face(line, currentMaterial));
	}


	private static void processVertexLine(String line, ObjModel model, Vec3f min, Vec3f max) {
		float[] vert = readFloatValues(line);
		if (vert.length > 2) {
			if (vert[0] > max.x) max.x = vert[0];
			if (vert[1] > max.y) max.y = vert[1];
			if (vert[2] > max.z) max.z = vert[2];
			if (vert[0] < min.x) min.x = vert[0];
			if (vert[1] < min.y) min.y = vert[1];
			if (vert[2] < min.z) min.z = vert[2];
		}
		model.verts.add(vert);
	}

	private static LineType getLineType(String line) {
		if (line.startsWith("v ")) return LineType.VERTEX;
		if (line.startsWith("vn ")) return LineType.NORMAL;
		if (line.startsWith("vt ")) return LineType.TEX_COORD;
		if (line.startsWith("f ")) return LineType.FACE;
		if (line.startsWith("mtllib ")) return LineType.MATERIAL_LIBRARY;
		if (line.startsWith("usemtl ")) return LineType.USE_MATERIAL;
		if (line.startsWith("g ")) return LineType.GROUP;
		return LineType.UNKNOWN;
	}

	private enum LineType {
		VERTEX,
		NORMAL,
		TEX_COORD,
		FACE,
		MATERIAL_LIBRARY,
		USE_MATERIAL,
		GROUP,
		UNKNOWN
	}

	protected static float[] readFloatValues(String line)
	{
		String[] parts = line.split("\\s+");
		float[] pos = new float[parts.length - 1];
		for (int i = 0; i < pos.length; i++)
			pos[i] = Float.parseFloat(parts[i + 1]);
		return pos;
	}

	public void render(GL2 gl)
	{
		ObjMaterial currentMaterial = null;
		gl.glEnable(GL.GL_TEXTURE_2D);
		for (ObjGroup group : groups) {
			for (Face face : group.faces) {
				if (currentMaterial == null || face.material != currentMaterial) {
					currentMaterial = face.material;
					currentMaterial.apply(gl);
				}
				gl.glBegin(GL2.GL_POLYGON);
				for (int i = 0; i < face.vertIndices.length; i++) {
					if (face.normalIndices != null)
						gl.glNormal3fv(normals.get(face.normalIndices[i]), 0);
					if (face.texCoordIndices != null)
						gl.glTexCoord2fv(texCoords.get(face.texCoordIndices[i]), 0);
					gl.glVertex3fv(verts.get(face.vertIndices[i]), 0);
				}
				gl.glEnd();
			}
		}
		gl.glDisable(GL.GL_TEXTURE_2D);
	}

	@Override
	public void dispose(GL gl)
	{
		for (ObjMaterial m : mtllib.materials)
			if (m.texture != null)
				m.texture.dispose(gl);
		disposed = true;
	}

	@Override
	public boolean isDisposed()
	{
		return disposed;
	}
}
