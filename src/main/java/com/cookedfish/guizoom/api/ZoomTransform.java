package com.cookedfish.guizoom.api;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * A scale applied to the GUI pose stack, anchored on a single GUI space point.
 * <p>
 * Because the transform is
 * <pre>anchor + scale * (point - anchor)</pre>
 * the anchor itself is a fixed point of the transform. When the anchor is the mouse cursor this
 * means the very pixel that is under the pointer keeps its GUI coordinate, so vanilla hit testing
 * ({@code mouseX}/{@code mouseY} handed to screens) still matches what the player sees.
 * <p>
 * A uniform scale has exactly one fixed point, so any other anchor shifts the pointer away from the
 * pixel it is visually on. {@link #inverseX(double)} / {@link #inverseY(double)} convert a pointer
 * position back into the coordinate space the un-zoomed UI expects, which is how GUI Zoom keeps
 * clicks, hover highlighting and tooltips accurate for centre anchored modes as well.
 *
 * @param scale   magnification factor, 1.0 means "do nothing"
 * @param anchorX anchor X in GUI scaled coordinates
 * @param anchorY anchor Y in GUI scaled coordinates
 */
public record ZoomTransform(double scale, double anchorX, double anchorY) {

    public static final ZoomTransform IDENTITY = new ZoomTransform(1.0D, 0.0D, 0.0D);

    /** @return true when this transform would not change anything. */
    public boolean isIdentity() {
        return Math.abs(this.scale - 1.0D) < 1.0E-4D;
    }

    /**
     * Pushes nothing: the caller is responsible for {@link PoseStack#pushPose()} /
     * {@link PoseStack#popPose()}. This only multiplies the transform onto the current pose.
     */
    public void apply(PoseStack pose) {
        if (this.isIdentity()) {
            return;
        }
        pose.translate(this.anchorX, this.anchorY, 0.0D);
        pose.scale((float) this.scale, (float) this.scale, 1.0F);
        pose.translate(-this.anchorX, -this.anchorY, 0.0D);
    }

    /** Maps a pointer X back to the logical X of the widget that is drawn there. */
    public double inverseX(double x) {
        return this.isIdentity() ? x : this.anchorX + (x - this.anchorX) / this.scale;
    }

    /** Maps a pointer Y back to the logical Y of the widget that is drawn there. */
    public double inverseY(double y) {
        return this.isIdentity() ? y : this.anchorY + (y - this.anchorY) / this.scale;
    }

    /** Maps a pointer delta back into logical units. */
    public double inverseDelta(double delta) {
        return this.isIdentity() ? delta : delta / this.scale;
    }

    /** @return true when a pointer at this position does not sit on the widget drawn below it. */
    public boolean shiftsPointer(double x, double y) {
        if (this.isIdentity()) {
            return false;
        }
        return Math.abs(this.inverseX(x) - x) >= 0.5D || Math.abs(this.inverseY(y) - y) >= 0.5D;
    }
}
