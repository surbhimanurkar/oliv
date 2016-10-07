package in.parapluie.app;

import android.app.Application;
import android.support.multidex.MultiDex;

import com.batch.android.Batch;
import com.batch.android.Config;
import com.facebook.FacebookSdk;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by surbhimanurkar on 03-03-2016.
 */
public class ParapluieApplication extends Application{

    @Override
    public void onCreate() {
        super.onCreate();
        FacebookSdk.sdkInitialize(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        MultiDex.install(this);

        //Settings for Batch.com push notifications
        Batch.Push.setGCMSenderId(getResources().getString(R.string.gcm_sender_id));
        Batch.Push.setManualDisplay(true);
        Batch.setConfig(new Config(getResources().getString(R.string.batch_api_key)));
    }
}
