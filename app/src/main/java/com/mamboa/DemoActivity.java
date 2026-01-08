package com.mamboa;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class DemoActivity extends AppCompatActivity {

    private static final String TAG_MAIN_ACTIVITY_FRAGMENT = "MainActivityFragment";
    private static final String TAG_MAIN_ACTIVITY_2_FRAGMENT = "MainActivity2Fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        Button layoutXmlButton = findViewById(R.id.layoutXmlButton);
        Button jetpackComposeButton = findViewById(R.id.jetpackComposeButton);
        
        // Display the LayoutXML fragment by default if no fragment is currently in the container.
        // This handles initial creation and ensures a fragment is present if not restored by the system.
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            Fragment initialFragment = new MainActivityFragment();
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, initialFragment, TAG_MAIN_ACTIVITY_FRAGMENT)
                .commit();
        }

        layoutXmlButton.setOnClickListener(v -> 
            showSpecificFragment(MainActivityFragment.class, TAG_MAIN_ACTIVITY_FRAGMENT)
        );

        jetpackComposeButton.setOnClickListener(v -> 
            showSpecificFragment(MainActivity2Fragment.class, TAG_MAIN_ACTIVITY_2_FRAGMENT)
        );
    }

    private void showSpecificFragment(Class<? extends Fragment> fragmentClass, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment currentFragmentInContainer = fm.findFragmentById(R.id.fragment_container);

        // If the desired fragment type is already displayed in the container, do nothing.
        if (fragmentClass.isInstance(currentFragmentInContainer)) {
            return;
        }

        FragmentTransaction transaction = fm.beginTransaction();
        Fragment fragmentToShow = fm.findFragmentByTag(tag);

        if (fragmentToShow == null) {
            // Fragment instance not found by tag, so create a new one.
            try {
                // Assumes fragmentClass has a public no-argument constructor.
                fragmentToShow = fragmentClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                // In a real app, log this error appropriately.
                e.printStackTrace(); 
                // Optionally, show an error message to the user or throw a RuntimeException.
                // For this rewrite, we'll just return to prevent a crash if instantiation fails.
                return; 
            }
        }
        // At this point, fragmentToShow is either the existing instance found by tag,
        // or a newly created one.

        // Replace the current fragment in the container with fragmentToShow,
        // and associate it with the given tag.
        transaction.replace(R.id.fragment_container, fragmentToShow, tag);
        
        // Add the transaction to the back stack, as per the original behavior for button clicks.
        transaction.addToBackStack(null); 
        
        transaction.commit();
    }
}