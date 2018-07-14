package com.android.biketrack.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.biketrack.R;
import com.android.biketrack.io.file.TrackFileFormat;
import com.android.biketrack.ui.fragment.HomeFragment;
import com.android.biketrack.ui.fragment.ScanFragment;
import com.android.biketrack.ui.fragment.SettingsFragment;

public class MainActivity extends AppCompatActivity implements HomeFragment.OnFragmentInteractionListener,
        ScanFragment.OnFragmentInteractionListener, SettingsFragment.OnFragmentInteractionListener {

    public static final String STATE_NAV_ITEM_INDEX = "NAV_ITEM_INDEX";
    private NavigationView mNavigationView;
    private DrawerLayout mDrawer;
    private View mNavHeader;
    private TextView mTxtName, mTxtWebsite;
    private Toolbar mToolbar;
    private FloatingActionButton mStartButtton;

    // Index to identify current nav menu item
    public static int mNavItemIndex = 0;

    // Tags used to attach the fragments
    private static final String[] FRAGMENT_TAGS = {"home", "scan", "settings"};

    // Toolbar titles respected to selected nav menu item
    private String[] mFragmentTitles;

    // Flag to load home fragment when user presses back key
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mHandler = new Handler();

        mDrawer = findViewById(R.id.drawer_layout);
        mNavigationView = findViewById(R.id.nav_view);
        mStartButtton = findViewById(R.id.fab);

        // Navigation view header
        mNavHeader = mNavigationView.getHeaderView(0);
        mTxtName = mNavHeader.findViewById(R.id.name);
        mTxtWebsite = mNavHeader.findViewById(R.id.website);

        // Load toolbar titles from string resources
        mFragmentTitles = getResources().getStringArray(R.array.nav_item_activity_titles);

        mStartButtton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //return new Intent(context, cls).addFlags(
                //        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                Intent intent = new Intent(getApplicationContext(), SaveActivity.class)
                        .putExtra(SaveActivity.EXTRA_TRACK_IDS, new long[] { 0 })
                        .putExtra(SaveActivity.EXTRA_TRACK_FILE_FORMAT, (Parcelable) TrackFileFormat.TCX);
                startActivity(intent);
            }
        });

        // Set nav menu header data
        mTxtName.setText(getString(R.string.app_name));
        mTxtWebsite.setText("Merida BIG Nine 900");  // TODO Bike name preference

        // Initializing navigation menu
        setUpNavigationView();

        if (savedInstanceState == null) {
            mNavItemIndex = 0;
        } else {
            mNavItemIndex = savedInstanceState.getInt(STATE_NAV_ITEM_INDEX);
        }

        loadFragment(mNavItemIndex);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);

        outState.putInt(STATE_NAV_ITEM_INDEX, mNavItemIndex);
    }

    /***
     * Returns respected fragment that user
     * selected from navigation menu
     */
    private void loadFragment(final int index) {
        // Selecting appropriate nav menu item
        selectNavMenu(index);

        // Set toolbar title
        setToolbarTitle(index);

        // Sometimes, when fragment has huge data, screen seems hanging
        // when switching between navigation menus
        // So using runnable, the fragment is loaded with cross fade effect
        // This effect can be seen in GMail app
        Runnable mPendingRunnable = new Runnable() {
            @Override
            public void run() {
                // Update the main content by replacing fragments
                Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAGS[index]);

                if (fragment == null) {
                   fragment = getFragment();
                }

                FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                fragmentTransaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
                fragmentTransaction.replace(R.id.frame, fragment, FRAGMENT_TAGS[index]);
                fragmentTransaction.commit();
            }
        };

        // Add to the message queue
        mHandler.post(mPendingRunnable);

        // Show or hide the fab button
        toggleFab();

        // Closing mDrawer on item click
        mDrawer.closeDrawers();

        // Refresh mToolbar menu
        invalidateOptionsMenu();
    }

    private Fragment getFragment() {
        switch (mNavItemIndex) {
            case 0:
                // Home
                return new HomeFragment();
            case 1:
                // Scan BLE devices
                return new ScanFragment();
            case 2:
                // Settings fragment
                return new SettingsFragment();
            default:
                return new HomeFragment();
        }
    }

    private void setToolbarTitle(int index) {
        getSupportActionBar().setTitle(mFragmentTitles[index]);
    }

    private void selectNavMenu(int index) {
        mNavigationView.getMenu().getItem(index).setChecked(true);
    }

    private void setUpNavigationView() {
        // Setting Navigation View Item Selected Listener to handle the item click of the navigation menu
        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {

            // This method will trigger on item Click of navigation menu
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {

                // Check to see which item was being clicked and perform appropriate action
                switch (menuItem.getItemId()) {
                    // Replacing the main content with ContentFragment Which is our Inbox View;
                    case R.id.nav_home:
                        mNavItemIndex = 0;
                        break;
                    case R.id.nav_scan:
                        mNavItemIndex = 1;
                        break;
                    case R.id.nav_settings:
                        mNavItemIndex = 2;
                        break;
                    case R.id.nav_about:
                        // Launch new intent instead of loading fragment
                        startActivity(new Intent(MainActivity.this, AboutActivity.class));
                        mDrawer.closeDrawers();
                        return true;
                    default:
                        mNavItemIndex = 0;
                }

                // Checking if the item is in checked state or not, if not make it in checked state
                if (menuItem.isChecked()) {
                    menuItem.setChecked(false);
                } else {
                    menuItem.setChecked(true);
                }
                menuItem.setChecked(true);

                loadFragment(mNavItemIndex);

                return true;
            }
        });


        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawer, mToolbar, R.string.openDrawer, R.string.closeDrawer) {

            @Override
            public void onDrawerClosed(View drawerView) {
                // Code here will be triggered once the mDrawer closes as we dont want anything to happen so we leave this blank
                super.onDrawerClosed(drawerView);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                // Code here will be triggered once the mDrawer open as we dont want anything to happen so we leave this blank
                super.onDrawerOpened(drawerView);
            }
        };

        // Setting the actionbarToggle to mDrawer layout
        mDrawer.setDrawerListener(actionBarDrawerToggle);

        // Calling sync state is necessary or else your hamburger icon wont show up
        actionBarDrawerToggle.syncState();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (mNavItemIndex == 0) {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                super.onBackPressed();
            } else {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                mNavItemIndex = 0;
                loadFragment(mNavItemIndex);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        // Show menu only when home fragment is selected
        if (mNavItemIndex == 0) {
            getMenuInflater().inflate(R.menu.main, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            Toast.makeText(getApplicationContext(), "Logout user!", Toast.LENGTH_LONG).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // show or hide the mStartButtton
    private void toggleFab() {
        if (mNavItemIndex == 0)
            mStartButtton.show();
        else
            mStartButtton.hide();
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}