package live.ptiv.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Explicit, ephemeral menus for the Orb-triangle prototype.
 * No network service, account access, persistent microphone, or file changes.
 */
final class PearlExperience {
    static final int VOICE_REQUEST=353;
    interface Controls {
        void invoke(String action);
        String trackTitle();
        int trackCount();
    }
    private final Activity host;
    private final Controls controls;
    PearlExperience(Activity host, Controls controls){this.host=host;this.controls=controls;}

    void orbitUnlocked(){
        Toast.makeText(host,"Second Pearl unlocked. Drag it onto a control to explore.",Toast.LENGTH_LONG).show();
    }

    void side(int side,boolean advanced){
        if(side<0||side>2)return;
        final String[] labels;
        final String[] actions;
        final String heading;
        if(advanced){
            heading="PURPLE PEARL • SECOND MENU";
            if(side==PearlTriangleGeometry.LEFT){
                labels=new String[]{"Performer Mode • offline audio sharing","About Performer Mode"};
                actions=new String[]{"performer","about-performer"};
            }else if(side==PearlTriangleGeometry.RIGHT){
                labels=new String[]{"My music services","About music services"};
                actions=new String[]{"services","about-services"};
            }else{
                labels=new String[]{"Voice interactions","Help / gesture guide","Inspect current track"};
                actions=new String[]{"voice","help","inspect-track"};
            }
        }else{
            heading="PEARL TRIANGLE • QUICK MENU";
            if(side==PearlTriangleGeometry.LEFT){
                labels=new String[]{"Browse / scroll offline songs","Current track information"};
                actions=new String[]{"library","inspect-track"};
            }else if(side==PearlTriangleGeometry.RIGHT){
                labels=new String[]{"Lyrics / Cosmic Karaoke","About lyrics"};
                actions=new String[]{"lyrics","about-lyrics"};
            }else{
                labels=new String[]{"Play / pause","Shuffle","Volume down","Volume up","Gesture help"};
                actions=new String[]{"play","shuffle","volume-down","volume-up","help"};
            }
        }
        new AlertDialog.Builder(host).setTitle(heading)
            .setItems(labels,(dialog,index)->run(actions[index]))
            .setNegativeButton("CLOSE",null).show();
    }

    void inspect(String key){
        final String description;
        final String action;
        switch(key){
            case "volume down":
                description="Decrease media output by one step.";action="volume-down";break;
            case "volume up":
                description="Increase media output by one step.";action="volume-up";break;
            case "previous":
                description="Go to the previous on-watch recording (or restart the current one).";action="previous";break;
            case "play":
                description="Start or pause the selected P.T. Architech recording.";action="play";break;
            case "next":
                description="Go to the next local P.T. Architech recording.";action="next";break;
            case "library":
                description="Open the native scrollable offline library.";action="library";break;
            case "lyrics":
                description="View Cosmic Karaoke. Accurate synchronized lyrics appear only if explicitly imported.";action="lyrics";break;
            case "shuffle":
                description="Turn shuffle on or off for the on-watch PTIV queue.";action="shuffle";break;
            case "seek":
                description="The orange playback timeline lets you seek. Touch it directly to choose the time.";action=null;break;
            case "track":
                description="Selected: "+controls.trackTitle()+"\nLocal, offline P.T. Architech playback.";action=null;break;
            case "offline":
                description=controls.trackCount()+" local P.T. Architech tracks indexed on this watch.";action=null;break;
            case "artwork":
                description="The Brooklyn skyline and three-sided Pearl controller. Drag the large Pearl to a side for quick menus; trace a full circuit for the second Pearl.";action=null;break;
            case "branding":
                description="P.T. Architech Neon Walkman. Seven Pearl taps reveal Performer Mode; eleven reveal Services Mode. A ten-second hold changes the triangle menu color.";action=null;break;
            default:
                description="Move the second Pearl onto any transport button or labeled area to inspect it. Return both Pearls to the triangle for voice.";action=null;
        }
        AlertDialog.Builder b=new AlertDialog.Builder(host)
            .setTitle("PEARL • "+key.toUpperCase(Locale.ROOT))
            .setMessage(description)
            .setNegativeButton("CLOSE",null);
        if(action!=null)b.setPositiveButton("USE",(d,i)->run(action));
        b.show();
    }

    private void run(String action){
        switch(action){
            case "voice": voice();return;
            case "about-performer":
                info("PERFORMER MODE","Choose a song or set, then use an available local file-sharing app. Actual audio files must be saved by the sound operator for offline performance. QR links alone are not offline audio.");return;
            case "about-services":
                info("MUSIC SERVICES","The optional hub opens provider-supported apps and HTTPS shortcuts. Provider subscriptions, accounts, protected streams and downloads stay under their official apps.");return;
            case "about-lyrics":
                info("LYRICS","Lyrics must be checked against the exact mastered recording. No unverified transcript or made-up timestamps are loaded.");return;
            case "inspect-track":inspect("track");return;
            case "help":
                info("TWO-PEARL GUIDE","Drag the large Pearl to LEFT for Library, RIGHT for Lyrics, BOTTOM for playback controls. Hold it 10 seconds: the triangle changes color and reveals Performer/Services. Trace its three sides and return to your start point: a second Pearl appears. Move the small Pearl to inspect on-screen controls; move it inside the triangle to unlock the voice prompt. Swipe up from the track title to open the scrollable library. Seven or eleven quick taps still work.");return;
            default:controls.invoke(action);
        }
    }

    void voice(){
        new AlertDialog.Builder(host).setTitle("TWO PEARLS • VOICE")
            .setMessage("The two Pearls are together inside the triangle.\n\nSPEAK opens the watch's installed speech recognizer ONCE. Commands: play, pause, next, previous, volume up, volume down, library, lyrics, shuffle, or track count.\n\nThe microphone is not always listening. Recognition requests offline processing but some Wear OS speech providers may need internet.")
            .setPositiveButton("SPEAK",(dialog,which)->startSpeech())
            .setNegativeButton("CANCEL",null).show();
    }
    private void startSpeech(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"PTIV: play, next, library");
        try{host.startActivityForResult(i,VOICE_REQUEST);}
        catch(Exception e){
            info("VOICE UNAVAILABLE","No supported speech recognition activity was available on the watch. The Pearl menus and offline playback still work. There is no automatic microphone recording.");
        }
    }
    void voiceResult(int resultCode,Intent data){
        if(resultCode!=Activity.RESULT_OK||data==null)return;
        ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(words==null||words.isEmpty())return;
        String command=words.get(0).trim().toLowerCase(Locale.ROOT);
        String action;
        switch(command){
            case "play":case "start music":case "resume":action="resume";break;
            case "pause":case "pause music":case "stop music":action="pause";break;
            case "next":case "next song":case "next track":action="next";break;
            case "previous":case "previous song":case "previous track":action="previous";break;
            case "volume up":case "louder":action="volume-up";break;
            case "volume down":case "quieter":action="volume-down";break;
            case "library":case "open library":action="library";break;
            case "lyrics":case "show lyrics":action="lyrics";break;
            case "shuffle":case "toggle shuffle":action="shuffle";break;
            case "track count":case "how many songs":
                info("OFFLINE LIBRARY",controls.trackCount()+" indexed P.T. Architech recordings.");return;
            default:
                Toast.makeText(host,"Unsupported command. Try play, pause, next, or library.",Toast.LENGTH_LONG).show();return;
        }
        controls.invoke(action);
    }
    private void info(String heading,String message){
        new AlertDialog.Builder(host).setTitle(heading).setMessage(message)
            .setPositiveButton("OK",null).show();
    }
}
