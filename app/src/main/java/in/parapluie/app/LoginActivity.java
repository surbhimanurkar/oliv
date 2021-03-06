package in.parapluie.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import in.parapluie.utils.Constants;
import in.parapluie.utils.FirebaseUtils;
import in.parapluie.utils.TitleFont;
import in.parapluie.utils.UsageAnalytics;
import com.batch.android.Batch;
import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;


public class LoginActivity extends BaseActivity{


    private static final String TAG = LoginActivity.class.getSimpleName();

    /* *************************************
     *              GENERAL                *
     ***************************************/

    /*Appname textview*/
    private TextView mAppName;

    /* A dialog that is presented until the Firebase authentication finished. */
    private ProgressDialog mAuthProgressDialog;

    /* A reference to the Firebase */
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mFirebaseRef;

    /* A reference to the Firebase User data */
    private DatabaseReference mUserRef;

    /* Data from the authenticated user */
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private String mDisplayName;

    /* Listener for Firebase session changes */
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    /* *************************************
     *              FACEBOOK               *
     ***************************************/
    /* The login button for Facebook */
    private LoginButton mFacebookLoginButton;
    private TextView mLoginPrivacyPromise;
    /* The callback manager for Facebook */
    private CallbackManager mFacebookCallbackManager;
    /* Used to track user logging in/out off Facebook */
    private AccessTokenTracker mFacebookAccessTokenTracker;
    /*Login Manager*/
    private LoginManager mFacebookLoginManager;
    /*Sent initial message*/
    private boolean haveSentInitialMessage = false;

    private UsageAnalytics mUsageAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Initializing Analytics
        //Obtain the FirebaseAnalytics instance.
        mUsageAnalytics = new UsageAnalytics();
        mUsageAnalytics.initTracker(this);

        Log.d(TAG, "Activity Launched");

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        mFacebookLoginManager = LoginManager.getInstance();

        if(mFirebaseUser!=null)
        {
            SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_LOGIN,Context.MODE_PRIVATE);
            Boolean logoutAttempt = sharedPreferences.getBoolean(Constants.LOGIN_PREF_LOGOUT, false);
            Log.d(TAG, "Logout Attempt: " + logoutAttempt + " Shared Pref:" + sharedPreferences.toString());
            if(logoutAttempt){
                mFirebaseAuth.signOut();
                mFacebookLoginManager.logOut();
            }else{
                Log.d(TAG, "User is logged in already");
                redirectUserToMain();
            }
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

         /* Create the Firebase ref that is used for all authentication with Firebase */
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseRef = mFirebaseDatabase.getReference();
        mUserRef = mFirebaseRef.child(Constants.F_NODE_USER);

        /*Facebook initializations*/
        mFacebookCallbackManager = CallbackManager.Factory.create();

        //Styling the app title
        //getSupportActionBar().hide();
        mAppName = (TextView) findViewById(R.id.app_name);
        mAppName.setTypeface(TitleFont.getInstance(this).getTypeFace());


        int loginTextSize = getResources().getDimensionPixelSize(R.dimen.abc_text_size_button_material);
        /* *************************************
         *              FACEBOOK               *
         ***************************************/
        /* Load the Facebook login button and set up the tracker to monitor access token changes */
        mFacebookLoginButton = (LoginButton) findViewById(R.id.login_with_facebook);
        mLoginPrivacyPromise = (TextView) findViewById(R.id.login_privacy_promise);
        customizeFacebookButton(mFacebookLoginButton, loginTextSize);
        mFacebookLoginButton.setReadPermissions("email", "public_profile");
        mFacebookLoginButton.registerCallback(mFacebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "facebook:onSuccess:" + loginResult);
                setLoadingScreen(true);
                handleFacebookAccessToken(loginResult);
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "facebook:onCancel");
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "facebook:onError", error);
            }
        });



        /* *************************************
         *               GENERAL               *
         ***************************************/


        /* Setup the progress dialog that is displayed later when authenticating with Firebase */
        mAuthProgressDialog = new ProgressDialog(this, R.style.LoadingSpinnerTheme);
        mAuthProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mAuthProgressDialog.setProgressDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.circular_progress_bar));
        mAuthProgressDialog.setCancelable(false);
        mAuthProgressDialog.show();
        mAuthProgressDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                mAuthProgressDialog.dismiss();
                mFirebaseUser = firebaseAuth.getCurrentUser();
                if (mFirebaseUser != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + mFirebaseUser.getUid());
                    setLoadingScreen(true);
                    setAuthenticatedUser();

                    //logging Login Event
                    mUsageAnalytics.trackLogin(mFirebaseUser.getUid(), mDisplayName);
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    setLoadingScreen(false);
                }
            }
        };
        /* Check if the user is authenticated with Firebase already. If this is the case we can set the authenticated
         * user and hide hide any login buttons */
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    /**
     * This method fires when any startActivityForResult finishes. The requestCode maps to
     * the value passed into startActivityForResult.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mFacebookCallbackManager.onActivityResult(requestCode, resultCode, data);
    }


    /**
     * Once a user is logged in, take the mAuthData provided from Firebase and "use" it.
     */
    private void setAuthenticatedUser() {
        Log.d(TAG, "SetAuthenticatedUser");
        if (mFirebaseUser != null) {
            /* Hide all the login buttons */
            mFacebookLoginButton.setVisibility(View.GONE);
            mLoginPrivacyPromise.setVisibility(View.GONE);
            saveUserData();
            redirectUserToMain();
            Batch.User.editor()
                    .setIdentifier(mFirebaseUser.getUid()) // Set to `null` if you want to remove the identifier.
                    .save();

        }
        /* invalidate options menu to hide/show the logout button */
        supportInvalidateOptionsMenu();
    }

    /**
     * Show errors to users
     */
    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }


    /* ************************************
     *             FACEBOOK               *
     **************************************
     */
    protected void customizeFacebookButton(LoginButton signInButton, int textSize) {

        //Styling Text
        signInButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        signInButton.setTypeface(null, Typeface.NORMAL);
        signInButton.setAllCaps(true);
    }
    private void handleFacebookAccessToken(final LoginResult loginResult) {
        AccessToken token = loginResult.getAccessToken();
        Log.d(TAG, "handleFacebookAccessToken:" + token);

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mFirebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());

                        //Save data from facebook
                        saveFacebookData(loginResult);
                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    private void saveFacebookData(LoginResult loginResult){
        // App code
        GraphRequest request = GraphRequest.newMeRequest(
                loginResult.getAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        Log.v("LoginActivity", response.toString());

                        // Application code
                        try {
                            Map<String,Object> map = new HashMap<String,Object>(5);
                            String gender = null;
                            if(object.has(Constants.FB_PROFILE_GENDER)) {
                                gender = object.getString(Constants.FB_PROFILE_GENDER);
                                map.put(Constants.F_KEY_USER_GENDER,gender);
                            }
                            if(object.has(Constants.FB_PROFILE_LINK)) {
                                map.put(Constants.FB_PROFILE_LINK,object.getString(Constants.FB_PROFILE_LINK));
                                Log.d("profile link",object.getString(Constants.FB_PROFILE_LINK));
                            }
                            if(object.has(Constants.FB_PROFILE_PICTURE)) {
                                map.put(Constants.FB_PROFILE_PICTURE,object.getString(Constants.FB_PROFILE_PICTURE));
                                Log.d("profile picture",object.getString(Constants.FB_PROFILE_PICTURE));
                            }
                            if(object.has(Constants.FB_PROFILE_AGE_RANGE))
                                map.put(Constants.F_KEY_USER_AGE_RANGE,object.getString(Constants.FB_PROFILE_AGE_RANGE));
                            if(object.has(Constants.FB_PROFILE_EMAIL))
                                map.put(Constants.F_KEY_USER_EMAIL,object.getString(Constants.FB_PROFILE_EMAIL));

                            //Add Gender to shared Pref
                            SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_LOGIN,Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            boolean isChatAllowed = true;
                            if(gender!=null && gender.equals(Constants.FB_PROFILE_GENDER_MALE))
                                isChatAllowed = false;
                            editor.putBoolean(Constants.LOGIN_PREF_ISCHATALLOWED, isChatAllowed);
                            editor.apply();

                            if(mFirebaseUser==null)
                                mFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                            mUserRef.child(mFirebaseUser.getUid())
                                    .child(Constants.F_NODE_USER_FB)
                                    .updateChildren(map);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,email,gender,link");
        request.setParameters(parameters);
        request.executeAsync();
    }



    /*
     * GENERAL
     */

    public void redirectUserToMain(){
        Log.d(TAG, "Redirecting to main");
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_LOGIN,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Constants.LOGIN_PREF_LOGOUT, false);
        editor.apply();
        Intent intent = new Intent(this,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        finish();
        startActivity(intent);
    }

    /**
     * Save User data in firebase
     */
    public void saveUserData(){
       //Saving basic user info
        Map<String, Object> map = new HashMap<String, Object>();
        if(mFirebaseUser.getDisplayName()!=null) {
            mDisplayName = mFirebaseUser.getDisplayName();
            map.put(Constants.F_KEY_USER_USERNAME, mDisplayName);
        }else if(mFirebaseUser.getProviderData()!=null){
            for (UserInfo userInfo : mFirebaseUser.getProviderData()) {
                if (mDisplayName == null && userInfo.getDisplayName() != null) {
                    mDisplayName = userInfo.getDisplayName();
                }
                map.put(Constants.F_KEY_USER_PROVIDER, userInfo.getProviderId());
            }
            map.put(Constants.F_KEY_USER_USERNAME, mDisplayName);
        }
        Log.d(TAG,"Tracking Login UID:" + mFirebaseUser.getUid());
        mUserRef.child(mFirebaseUser.getUid()).updateChildren(map);

        //Saving user tracking
        FirebaseUtils.getInstance().saveUserTracking();
    }





    public void setLoadingScreen(boolean show){
        if(show){
            mFacebookLoginButton.setVisibility(View.GONE);
            mLoginPrivacyPromise.setVisibility(View.GONE);
            //mAppName.setVisibility(View.GONE);
        }else{
            mFacebookLoginButton.setVisibility(View.VISIBLE);
            mLoginPrivacyPromise.setVisibility(View.VISIBLE);
            mAppName.setVisibility(View.VISIBLE);
        }
    }

}
