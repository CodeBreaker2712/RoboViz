/*
 *  Copyright 2011 RoboViz
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

package rv.ui.screens;

import javax.media.opengl.GL2;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.glu.GLU;
import js.jogl.view.Viewport;
import js.math.vector.Vec3f;
import rv.comm.rcssserver.GameState;
import rv.comm.rcssserver.GameState.GameStateChangeListener;
import rv.world.Team;
import rv.world.WorldModel;
import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Displays player positions from a 2D top-down view of field
 * 
 * @author justin
 */
public class Field2DOverlay implements Screen, GameStateChangeListener {

    private final WorldModel world;

    private float            fieldWidth   = 180;
    private float            fieldLength  = 120;
    private int              screenWidth  = 1;
    private int              screenHeight = 1;
    private boolean          visible      = false;

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public Field2DOverlay(WorldModel world) {
        this.world = world;
        world.getGameState().addListener(this);
    }

    @Override
    public void setEnabled(GLCanvas canvas, boolean enabled) {
    }

    private void setView(GL2 gl, GLU glu) {
        float hfw = fieldWidth / 2;
        float hfl = fieldLength / 2;

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(-hfl, hfl, -hfw, hfw, 1, 5);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        glu.gluLookAt(0, 4, 0, 0, 0, 0, 0, 0, 1);

        int displayWidth = (int) (screenWidth * 0.3f);
        int displayHeight = (int) (displayWidth * fieldWidth / fieldLength);
        gl.glViewport(20, 20, displayWidth, displayHeight);
    }

    private void unsetView(GL2 gl, Viewport vp) {
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopMatrix();
        vp.apply(gl);
    }

    private void drawPoints(GL2 gl, int pSize, boolean manualColor) {
        gl.glPointSize(pSize);
        gl.glBegin(GL2.GL_POINTS);
        Team left = world.getLeftTeam();
        if (!manualColor)
            gl.glColor3fv(left.getTeamMaterial().getDiffuse(), 0);
        for (int i = 0; i < left.getAgents().size(); i++) {
            Vec3f p = left.getAgents().get(i).getPosition();
            if (p != null) {
                gl.glVertex3f(p.x, p.y, p.z);
            }
        }
        Team right = world.getRightTeam();
        if (!manualColor)
            gl.glColor3fv(right.getTeamMaterial().getDiffuse(), 0);
        for (int i = 0; i < right.getAgents().size(); i++) {
            Vec3f p = right.getAgents().get(i).getPosition();
            if (p != null) {
                gl.glVertex3f(p.x, p.y, p.z);
            }
        }
        gl.glEnd();
        gl.glPointSize(pSize * 0.75f);
        gl.glBegin(GL2.GL_POINTS);
        Vec3f p = world.getBall().getPosition();
        if (p != null) {
            if (!manualColor)
                gl.glColor3f(1, 1, 1);
            gl.glVertex3f(p.x, p.y, p.z);
        }
        gl.glEnd();
    }

    @Override
    public void render(GL2 gl, GLU glu, GLUT glut, Viewport vp) {
        if (world.getField().getModel().isLoaded() && visible) {
            screenWidth = vp.w;
            screenHeight = vp.h;

            gl.glColor4f(1, 1, 1, 1);
            setView(gl, glu);
            world.getField().render(gl);

            int pSize = (int) (screenWidth * 0.01125);

            gl.glEnable(GL2.GL_POINT_SMOOTH);
            gl.glColor3f(0, 0, 0);
            drawPoints(gl, pSize, true);
            drawPoints(gl, pSize - 2, false);
            gl.glDisable(GL2.GL_POINT_SMOOTH);

            unsetView(gl, vp);
        }
    }

    @Override
    public void gsMeasuresAndRulesChanged(GameState gs) {
        fieldWidth = gs.getFieldWidth();
        fieldLength = gs.getFieldLength();
    }

    @Override
    public void gsPlayStateChanged(GameState gs) {
    }

    @Override
    public void gsTimeChanged(GameState gs) {
    }
}