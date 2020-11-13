package it.unive.reciak;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.webrtc.SurfaceViewRenderer;

public class ViewsManager {
    private final SurfaceViewRenderer mainView;
    private final SurfaceViewRenderer leftView;
    private final SurfaceViewRenderer rightView;

    private final ViewGroup.LayoutParams mainLayout;
    private final ViewGroup.LayoutParams leftLayout;
    private final ViewGroup.LayoutParams rightLayout;

    private final ConstraintLayout layout;

    public ViewsManager(@NonNull SurfaceViewRenderer mainView, @NonNull SurfaceViewRenderer leftView, @NonNull SurfaceViewRenderer rightView, @NonNull ConstraintLayout layout) {
        this.mainView = mainView;
        this.leftView = leftView;
        this.rightView = rightView;

        this.mainLayout = mainView.getLayoutParams();
        this.leftLayout = leftView.getLayoutParams();
        this.rightLayout = rightView.getLayoutParams();

        this.layout = layout;
    }

    // Sposta la view dell'utente che vuole registrare nella posizione di mainView
    public void swapViews(@NonNull SurfaceViewRenderer peerView) {
        if (peerView == leftView)
            swapLeft();
        else if (peerView == rightView)
            swapRight();
        else
            swapMain();
    }

    // Riposiziona mainVIew al centro
    private void swapMain() {
        hideViews();
        removeViews();
        mainView.setZOrderMediaOverlay(false);
        leftView.setZOrderMediaOverlay(true);
        rightView.setZOrderMediaOverlay(true);
        layout.addView(mainView, mainLayout);
        layout.addView(leftView, leftLayout);
        layout.addView(rightView, rightLayout);
        showViews();
    }

    // Sposta leftView al centro (mainView)
    private void swapLeft() {
        hideViews();
        removeViews();
        mainView.setZOrderMediaOverlay(true);
        leftView.setZOrderMediaOverlay(false);
        rightView.setZOrderMediaOverlay(true);
        layout.addView(mainView, rightLayout);
        layout.addView(leftView, mainLayout);
        layout.addView(rightView, leftLayout);
        showViews();
    }

    // Sposta rightView al centro (mainView)
    private void swapRight() {
        hideViews();
        removeViews();
        mainView.setZOrderMediaOverlay(true);
        leftView.setZOrderMediaOverlay(true);
        rightView.setZOrderMediaOverlay(false);
        layout.addView(mainView, rightLayout);
        layout.addView(leftView, leftLayout);
        layout.addView(rightView, mainLayout);
        showViews();
    }

    // Nasconde le view
    private void hideViews() {
        mainView.setVisibility(View.GONE);
        leftView.setVisibility(View.GONE);
        rightView.setVisibility(View.GONE);
    }

    // Mostra le view
    private void showViews() {
        mainView.setVisibility(View.VISIBLE);
        leftView.setVisibility(View.VISIBLE);
        rightView.setVisibility(View.VISIBLE);
    }

    // Rimuove le view da ConstraintLayout
    private void removeViews() {
        layout.removeView(mainView);
        layout.removeView(leftView);
        layout.removeView(rightView);
    }
}
