package com.android.biketrack.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.biketrack.Constants;
import com.android.biketrack.R;
import com.android.biketrack.io.file.TrackFileFormat;
import com.android.biketrack.ui.fragment.HomeFragment;
import com.android.biketrack.ui.fragment.ScanFragment;
import com.android.biketrack.ui.fragment.SettingsFragment;
import com.android.biketrack.utils.PreferencesUtils;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    public static final String STATE_NAV_ITEM_INDEX = "NAV_ITEM_INDEX";

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 0;
    private static final int REQUEST_ENABLE_BT = 1;

    private NavigationView mNavigationView;
    private DrawerLayout mDrawer;
    private View mNavHeader;
    private TextView mTxtAppName, mTxtBikeName;
    private Toolbar mToolbar;
    private FloatingActionButton mStartButton;

    // Index to identify current nav menu item
    public static int mNavItemIndex = 0;

    // Tags used to attach the fragments
    private static final String[] FRAGMENT_TAGS = {"home", "scan", "settings"};

    // Toolbar titles respected to selected nav menu item
    private String[] mFragmentTitles;

    private Handler mHandler;

    private boolean mEnableBTReq;
    private boolean mFirstDrawerOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mEnableBTReq = false;
        mFirstDrawerOpen = true;

        mHandler = new Handler();

        mDrawer = findViewById(R.id.drawer_layout);
        mNavigationView = findViewById(R.id.nav_view);
        mStartButton = findViewById(R.id.fab);

        // Navigation view header
        mNavHeader = mNavigationView.getHeaderView(0);
        mTxtAppName = mNavHeader.findViewById(R.id.app_name);
        mTxtBikeName = mNavHeader.findViewById(R.id.bike_name);

        // Load toolbar titles from string resources
        mFragmentTitles = getResources().getStringArray(R.array.nav_item_activity_titles);

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), SaveActivity.class)
                        .putExtra(SaveActivity.EXTRA_TRACK_IDS, new long[] { 0 })
                        .putExtra(SaveActivity.EXTRA_TRACK_FILE_FORMAT, (Parcelable) TrackFileFormat.TCX);
                startActivity(intent);
            }
        });

        // Set nav menu header data
        mTxtAppName.setText(getString(R.string.app_name));

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
    protected void onResume() {
        super.onResume();

        if (mEnableBTReq) {
            loadFragment(1);
            mEnableBTReq = false;
        }
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
                return HomeFragment.newInstance();
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
                    case R.id.nav_home:
                        mNavItemIndex = 0;
                        break;
                    case R.id.nav_scan:
                        mNavItemIndex = 1;

                        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                        final BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

                        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
                        // fire an intent to display a dialog asking the user to grant permission to enable it.
                        if (!bluetoothAdapter.isEnabled()) {
                            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                            mDrawer.closeDrawers();
                            return false;
                        }

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

                menuItem.setChecked(true);

                loadFragment(mNavItemIndex);

                return true;
            }
        });


        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawer, mToolbar, R.string.openDrawer, R.string.closeDrawer) {

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                mTxtBikeName.setText(PreferencesUtils.getString(getApplicationContext(), R.string.prefkey_bike_name, ""));

                final MenuItem scanBLEItem = mNavigationView.getMenu().getItem(1);

                // Use this check to determine whether BLE is supported on the device. Then you can
                // selectively disable BLE-related features.
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    if (mFirstDrawerOpen) {
                        Toast.makeText(getApplicationContext(), R.string.ble_not_supported, Toast.LENGTH_LONG).show();
                    }
                    scanBLEItem.setEnabled(false);
                }

                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

                // Checks if Bluetooth is supported on the device.
                if (bluetoothAdapter == null) {
                    if (mFirstDrawerOpen) {
                        Toast.makeText(getApplicationContext(), R.string.error_bluetooth_not_supported, Toast.LENGTH_LONG).show();
                    }
                    scanBLEItem.setEnabled(false);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android M Permission check
                    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                        builder.setTitle(R.string.Location_dialog_title);
                        builder.setMessage(R.string.location_dialog_mes);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                            PERMISSION_REQUEST_COARSE_LOCATION);
                                }
                            }
                        });
                        builder.show();
                    }
                }

                mFirstDrawerOpen = false;
            }
        };

        // Setting the actionbarToggle to mDrawer layout
        mDrawer.setDrawerListener(actionBarDrawerToggle);

        // Calling sync state is necessary or else your hamburger icon wont show up
        actionBarDrawerToggle.syncState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.location_dis_title);
                    builder.setMessage(R.string.location_dis_mes);
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            mEnableBTReq = (resultCode == Activity.RESULT_OK);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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

    // show or hide the mStartButton
    private void toggleFab() {
        if (mNavItemIndex == 0)
            mStartButton.show();
        else
            mStartButton.hide();
    }
}