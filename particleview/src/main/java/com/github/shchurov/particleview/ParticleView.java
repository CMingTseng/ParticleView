package com.github.shchurov.particleview;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class ParticleView extends FrameLayout {

    private GlTextureView glTextureView;
    private ParticleRenderer renderer;

    public ParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        glTextureView = new GlTextureView(getContext());
        glTextureView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        renderer = new ParticleRenderer();
        glTextureView.setRenderer(renderer);
        addView(glTextureView);
    }

    public void setTextureAtlasFactory(TextureAtlasFactory factory) {
        renderer.setTextureAtlasFactory(factory);
    }

    public void setParticleSystem(ParticleSystem system) {
        renderer.setParticleSystem(system);
    }

    public void startRendering() {
        glTextureView.startRendering();
    }

    public void stopRendering() {
        glTextureView.stopRendering();
    }

}
