package me.echeung.moemoekyun.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputEditText;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import me.echeung.moemoekyun.App;
import me.echeung.moemoekyun.R;
import me.echeung.moemoekyun.adapters.ViewPagerAdapter;
import me.echeung.moemoekyun.api.interfaces.AuthListener;
import me.echeung.moemoekyun.api.responses.Messages;
import me.echeung.moemoekyun.constants.Endpoints;
import me.echeung.moemoekyun.databinding.ActivityMainBinding;
import me.echeung.moemoekyun.service.RadioService;
import me.echeung.moemoekyun.utils.AuthUtil;
import me.echeung.moemoekyun.utils.NetworkUtil;
import me.echeung.moemoekyun.utils.SDKUtil;

public class MainActivity extends AppCompatActivity {

    public static final String TRIGGER_LOGIN_AND_FAVORITE = "fav_after_login";
    public static final String AUTH_EVENT = "auth_event";

    private ActivityMainBinding binding;

    private AlertDialog aboutDialog;

    private ViewPager viewPager;

    private BroadcastReceiver intentReceiver;
    private boolean receiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        binding.setVm(App.getRadioViewModel());

        // Check network connectivity
        binding.btnRetry.setOnClickListener(v -> retry());
        if (!NetworkUtil.isNetworkAvailable(this)) {
            return;
        }

        // Init app/tab bar
        initAppbar();

        // Sets audio type to media (volume button control)
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Invalidate token if needed
        AuthUtil.checkAuthTokenValidity(this);

        // Handle intent actions
        initBroadcastReceiver();
        handleIntentAction(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntentAction(intent);
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(intentReceiver);
            receiverRegistered = false;
        }

        // Kill service/notification if killing activity and not playing
        final RadioService service = App.getService();
        if (service != null && !service.isPlaying()) {
            final Intent stopIntent = new Intent(RadioService.STOP);
            sendBroadcast(stopIntent);
        }

        if (viewPager != null) {
            viewPager.setAdapter(null);
        }

        if (binding != null) {
            binding.unbind();
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (viewPager != null && viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0, true);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * For retry button in no internet view.
     */
    private void retry() {
        if (NetworkUtil.isNetworkAvailable(this)) {
            recreate();

            final Intent updateIntent = new Intent(RadioService.UPDATE);
            sendBroadcast(updateIntent);
        }
    }

    private void initBroadcastReceiver() {
        intentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleIntentAction(intent);
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.TRIGGER_LOGIN_AND_FAVORITE);

        registerReceiver(intentReceiver, intentFilter);
        receiverRegistered = true;
    }

    private void handleIntentAction(Intent intent) {
        final String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case MainActivity.TRIGGER_LOGIN_AND_FAVORITE:
                    showLoginDialog(this::favoriteSong);
                    break;
            }
        }
    }

    /**
     * Initializes everything for the tabs: the adapter, icons, and title handler
     */
    private void initAppbar() {
        // Set up app bar
        setSupportActionBar(binding.appbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Set up ViewPager and adapter
        viewPager = binding.pager;
        final ViewPagerAdapter mViewPagerAdapter = new ViewPagerAdapter(this, getSupportFragmentManager());
        viewPager.setAdapter(mViewPagerAdapter);

        // Set up tabs
        final TabLayout tabLayout = binding.tabs;
        tabLayout.setupWithViewPager(viewPager);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

        final int tabCount = tabLayout.getTabCount();
        for (int i = 0; i < tabCount; i++) {
            tabLayout.getTabAt(i).setIcon(mViewPagerAdapter.getIcon(i));
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // Toggle visibility of login/logout items based on authentication status
        final boolean authenticated = AuthUtil.isAuthenticated(this);
        menu.findItem(R.id.action_login).setVisible(!authenticated);
        menu.findItem(R.id.action_logout).setVisible(authenticated);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                if (AuthUtil.isAuthenticated(this)) {
                    startActivity(new Intent(this, SearchActivity.class));
                } else {
                    showLoginDialog(() -> onOptionsItemSelected(item));
                }
                return true;

            case R.id.action_login:
                showLoginDialog();
                return true;

            case R.id.action_logout:
                showLogoutDialog();
                return true;

            case R.id.action_about:
                showAboutDialog();
                return true;

            case R.id.action_browser:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Endpoints.SITE));
                startActivity(browserIntent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showLoginDialog() {
        showLoginDialog(null);
    }

    public void showLoginDialog(@Nullable final OnLoginListener listener) {
        final View layout = getLayoutInflater().inflate(R.layout.dialog_login, findViewById(R.id.layout_root));
        final TextInputEditText loginUser = layout.findViewById(R.id.login_username);
        final TextInputEditText loginPass = layout.findViewById(R.id.login_password);

        final AlertDialog loginDialog = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle)
                .setTitle(R.string.login)
                .setView(layout)
                .setPositiveButton(R.string.login, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        // Override the positive button listener so it won't automatically be dismissed even with
        // an error
        loginDialog.setOnShowListener(dialog -> {
            final Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                final String user = loginUser.getText().toString().trim();
                final String pass = loginPass.getText().toString().trim();

                if (user.length() == 0 || pass.length() == 0) {
                    return;
                }

                login(user, pass, dialog, listener);
            });
        });

        loginDialog.show();
    }

    /**
     * Logs the user in with the provided credentials.
     *
     * @param user     Username to pass in the request body.
     * @param pass     Password to pass in the request body.
     * @param dialog   Reference to the login dialog so it can be dismissed upon success.
     * @param listener Used to run something after a successful login.
     */
    private void login(final String user, final String pass, final DialogInterface dialog, final OnLoginListener listener) {
        App.getApiClient().authenticate(this, user, pass, new AuthListener() {
            @Override
            public void onFailure(final String result) {
                runOnUiThread(() -> {
                    String errorMsg = "";

                    switch (result) {
                        case Messages.INVALID_USER:
                            errorMsg = getString(R.string.error_name);
                            break;
                        case Messages.INVALID_PASS:
                            errorMsg = getString(R.string.error_pass);
                            break;
                        case Messages.ERROR:
                            errorMsg = getString(R.string.error_general);
                            break;
                    }

                    Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onSuccess(final String result) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    invalidateOptionsMenu();

                    broadcastAuthEvent();

                    if (listener != null) {
                        listener.onLogin();
                    }
                });
            }
        });
    }

    private void broadcastAuthEvent() {
        final Intent authEventIntent = new Intent(MainActivity.AUTH_EVENT);
        sendBroadcast(authEventIntent);
    }

    private void favoriteSong() {
        final Intent favIntent = new Intent(RadioService.TOGGLE_FAVORITE);
        sendBroadcast(favIntent);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle)
                .setTitle(R.string.logout)
                .setMessage(getString(R.string.logout_confirmation))
                .setPositiveButton(R.string.logout, (dialogInterface, i) -> logout())
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private void logout() {
        if (!AuthUtil.isAuthenticated(this)) {
            return;
        }

        AuthUtil.clearAuthToken(this);
        Toast.makeText(getApplicationContext(), getString(R.string.logged_out), Toast.LENGTH_LONG).show();
        invalidateOptionsMenu();

        broadcastAuthEvent();
    }

    private void showAboutDialog() {
        if (aboutDialog == null) {
            String version;
            try {
                version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                version = "";
            }

            aboutDialog = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle)
                    .setTitle(getString(R.string.about_app_title, getString(R.string.app_name), version))
                    .setMessage(SDKUtil.fromHtml(getString(R.string.about_content)))
                    .setPositiveButton(R.string.close, null)
                    .create();
        }

        aboutDialog.show();

        final TextView textContent = aboutDialog.findViewById(android.R.id.message);
        textContent.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public interface OnLoginListener {
        void onLogin();
    }
}