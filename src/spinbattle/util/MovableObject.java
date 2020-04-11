package spinbattle.util;

import math.Vector2d;
import spinbattle.core.Planet;
import spinbattle.core.VectorField;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;

public class MovableObject {
    public Vector2d s;
    public Vector2d v;

    // may not need this...
    boolean isActive = false;

    public MovableObject copy() {
        MovableObject mo = new MovableObject();
        mo.s = s.copy();
        mo.v = v.copy();
        return mo;
    }

    public MovableObject update(VectorField vf) {
        if (vf == null) {
            s.add(v);
        } else {
            v.add(vf.getForce(s), vf.getForceConstant());
            s.add(v);
        }
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MovableObject other = (MovableObject) obj;
        return s.equals(other.s) &&
                v.equals(other.v);
    }

    public String toString() {
        return s + " :: " + v;
    }

}
