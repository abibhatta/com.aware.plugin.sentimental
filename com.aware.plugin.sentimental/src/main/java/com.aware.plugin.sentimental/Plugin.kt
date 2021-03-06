package com.aware.plugin.sentimental

import android.app.Service
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.aware.Applications
import com.aware.Aware
import com.aware.Aware_Preferences
import com.aware.providers.Applications_Provider
import com.aware.providers.Keyboard_Provider
import com.aware.utils.Aware_Plugin
import com.aware.plugin.sentimental.*
import org.json.JSONObject
import java.util.HashMap
//import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T



open class Plugin : Aware_Plugin() {

    companion object {
        const val PACKAGE_NAME = "packageName"
        const val TYPED_TEXT = "typedText"

        interface AWARESensorObserver {
            fun onTextContextChanged(data: ContentValues)
        }

        var awareSensor: AWARESensorObserver ?= null

        fun setSensorObserver(observer: AWARESensorObserver) {
            awareSensor = observer
        }

        fun getSensorObserver(): AWARESensorObserver {
            return awareSensor!!
        }
    }

    /**
     * The package name of where the keyboard was interesting to track
     */
    var keyboardInApp = ""

    /**
     * List that contains the device's installed keyboard methods
     */
    var installedKeyboards = ""



    /**
     * Where we keep the buffer of written text
     */
    var textBuffer = ""
    //adding variable that is overwritten to store final text value without edits
    var textBufferNew = ""
    //adding variable for previous text
    var prevTextBuffer =""

    override fun onCreate() {
        Log.i("ABTest","This is echoed on create");
        super.onCreate()
        TAG = "AWARE: Sentimental"

        val usingInput = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        installedKeyboards = usingInput.enabledInputMethodList.toString()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i("ABTest","This is echoed on start");
        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true")

            if (Aware.getSetting(applicationContext, Settings.STATUS_PLUGIN_SENTIMENTAL).isEmpty()) {
                Aware.setSetting(applicationContext, Settings.STATUS_PLUGIN_SENTIMENTAL, true)
            } else {
                if (Aware.getSetting(applicationContext, Settings.STATUS_PLUGIN_SENTIMENTAL).equals("false", ignoreCase = true)) {
                    Aware.stopPlugin(applicationContext, packageName)
                    return Service.START_STICKY
                }
            }
            Log.i("ABTest","this is echoed if permissions are correct");
            if (Applications.isAccessibilityServiceActive(this)) {
                Log.i("ABTest", "This is echoed before set sensor observer");
                Aware.setSetting(this, Aware_Preferences.STATUS_APPLICATIONS, true)
                Aware.startKeyboard(this);

                //load dictionary
                val Sentiment =SentimentAnalysis(this).getInstance();
                Log.i("ABTest", "Sentiment object is ");

                Log.i("ABTest", Sentiment.dictionaryAsString);
                //val testHash = Sentiment.getScoreFromInput("day hello1 there");
                //for ((key, value) in testHash) {
                //    Log.i("ABTest", key.toString() + " " + value.toString())
                //}

                Applications.setSensorObserver(object : Applications.AWARESensorObserver {

                    override fun onCrash(data: ContentValues?) {}
                    override fun onNotification(data: ContentValues?) {}
                    override fun onBackground(data: ContentValues?) {}
                    override fun onKeyboard(data: ContentValues?) {
                        val packagesOfInterest = Aware.getSetting(applicationContext, Settings.PLUGIN_SENTIMENTAL_PACKAGES).split(",")
                        Log.i("ABTest","package data is "+data!!.getAsString(Keyboard_Provider.Keyboard_Data.PACKAGE_NAME));
                        if (packagesOfInterest.contains(data!!.getAsString(Keyboard_Provider.Keyboard_Data.PACKAGE_NAME))) {
                            keyboardInApp = data!!.getAsString(Keyboard_Provider.Keyboard_Data.PACKAGE_NAME)
                            textBuffer =textBuffer.plus(". ").plus(data.getAsString(Keyboard_Provider.Keyboard_Data.CURRENT_TEXT))
                            var tempCurrTextBuffer=data.getAsString(Keyboard_Provider.Keyboard_Data.CURRENT_TEXT);
                            var tempTextBuffer=data.getAsString(Keyboard_Provider.Keyboard_Data.BEFORE_TEXT);
                            Log.i("ABTest","Current Text is ");
                            Log.i("ABTest",tempCurrTextBuffer);
                            Log.i("ABTest","Before text is");
                            Log.i("ABTest",tempTextBuffer);
                            //log only when input ends for corrected text values only
                            // this happens when prevTextBuffer has 0 length and textBufferNew has length>0
                            if (tempCurrTextBuffer.length>0 && tempTextBuffer.length==0){

                                Log.i("ABTest","After corrections prev text is");
                                Log.i("ABTest",textBufferNew);
                                val testHash = Sentiment.getScoreFromInput(textBufferNew);
                            }
                            textBufferNew= tempCurrTextBuffer;
                            prevTextBuffer = tempTextBuffer;


                        }
                    }

                    override fun onTouch(data: ContentValues?) {}
                    override fun onForeground(data: ContentValues?) {
                        Log.i("ABTest","echoed on foreground");
                        val currentApp = data!!.getAsString(Applications_Provider.Applications_Foreground.PACKAGE_NAME)
                        if (installedKeyboards.contains(currentApp)) return //we ignore foreground package of keyboard input

                        if (!textBuffer.isEmpty() && currentApp != keyboardInApp) { //we were using an app of interest and changed app

                            val contentValues = ContentValues()
                            contentValues.put(Plugin.PACKAGE_NAME, keyboardInApp)
                            contentValues.put(Plugin.TYPED_TEXT, textBuffer)

                            Plugin.awareSensor?.onTextContextChanged(contentValues)
                            Log.i("ABTest","Echoed before reset"+textBufferNew);
                            if (textBufferNew.length>0){
                                val testHash = Sentiment.getScoreFromInput(textBufferNew);
                            }
                            textBuffer = ""
                            textBufferNew = ""
                            prevTextBuffer = "";
                            keyboardInApp = ""
                        }
                    }
                })
            }

            if (Aware.isStudy(this)) {
                val aware_account = Aware.getAWAREAccount(applicationContext)
                val authority = Provider.getAuthority(applicationContext)
                val frequency = java.lang.Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60

                ContentResolver.setIsSyncable(aware_account, authority, 1)
                ContentResolver.setSyncAutomatically(aware_account, authority, true)
                val request = SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(aware_account, authority)
                        .setExtras(Bundle()).build()
                ContentResolver.requestSync(request)
            }
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Aware.setSetting(this, Aware_Preferences.STATUS_APPLICATIONS, false)
        Aware.setSetting(this, Settings.STATUS_PLUGIN_SENTIMENTAL, false)
        Aware.stopKeyboard(this)

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Provider.getAuthority(this),
                Bundle.EMPTY
        );
    }
}
