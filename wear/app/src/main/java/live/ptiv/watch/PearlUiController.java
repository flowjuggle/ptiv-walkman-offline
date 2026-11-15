package live.ptiv.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/** Menus are instantiated only after a gesture. No timers, resident services or microphone. */
final class PearlUiController {
    static final int SPEECH_REQUEST=353;
    interface Controls {
        void library();
        void help();
        void playPause();
        void next();
        void previous();
        void volume(int delta);
        void lyrics();
        void performer();
        void services();
        void shuffle();
    }

    private final Activity host;
    private final Controls controls;
    PearlUiController(Activity host,Controls controls){
        this.host=host;this.controls=controls;
    }

    void onSide(int page,int side){
        if(page==0){
            if(side==PearlTriangleMath.LEFT)controls.library();
            else if(side==PearlTriangleMath.RIGHT)showControls();
            else showHelp();
        }else{
            if(side==PearlTriangleMath.LEFT)controls.performer();
            else if(side==PearlTriangleMath.RIGHT)controls.services();
            else requestVoice();
        }
    }

    void showHelp(){
        info("Hold and drag the Pearl toward a triangle side:\n"+
            "LEFT: song library\nRIGHT: playback controls\nBOTTOM: help\n\n"+
            "Hold the center Pearl for 10 seconds to change triangle color and show SEND / APPS / VOICE.\n\n"+
            "Hold then trace around ALL THREE triangle sides to create a movable second Pearl. Drag it onto any control to learn about it or activate it. Bring that second Pearl into the triangle to request voice.\n\n"+
            "Swipe up on blank space for the song list. All ordinary playback controls still work.");
    }

    void showControls(){
        final String[] labels={"PLAY / PAUSE","NEXT TRACK","PREVIOUS TRACK","VOLUME +","VOLUME −",
            "TRACK LIBRARY","LYRICS","SHUFFLE","HELP"};
        new AlertDialog.Builder(host).setTitle("PEARL • CONTROLS")
            .setItems(labels,(dialog,which)->{
                switch(which){
                    case 0: controls.playPause();break;
                    case 1: controls.next();break;
                    case 2: controls.previous();break;
                    case 3: controls.volume(1);break;
                    case 4: controls.volume(-1);break;
                    case 5: controls.library();break;
                    case 6: controls.lyrics();break;
                    case 7: controls.shuffle();break;
                    default: showHelp();
                }
            }).setNegativeButton("CLOSE",null).show();
    }

    void onOrbit(){
        new AlertDialog.Builder(host).setTitle("SECOND PEARL UNLOCKED")
            .setMessage("A smaller turquoise Pearl is now on the screen. Drag it to any control for information and an optional action. Move it inside the triangle to request voice interaction. Nothing is shared or recorded automatically.")
            .setPositiveButton("GOT IT",null).show();
    }

    void inspect(float x,float y){
        int action=PearlTriangleMath.actionAt(x,y);
        String title,about;
        switch(action){
            case 1:title="Volume down";about="Lower the watch's current media output one step.";break;
            case 2:title="Previous song";about="Go to the previous P.T. Architech offline track.";break;
            case 3:title="Play / pause";about="Start or pause the current P.T. Architech track.";break;
            case 4:title="Next song";about="Advance to the next offline P.T. Architech track.";break;
            case 5:title="Volume up";about="Increase the watch's current media output one step.";break;
            case 6:title="Music library";about="Browse and scroll through indexed offline PTIV songs.";break;
            case 7:title="Lyrics";about="Open Cosmic Karaoke. Only verified timed lyrics are intended for final release.";break;
            case 8:title="Shuffle";about="Turn random track selection on or off.";break;
            case 9:title="Scrub";about="Drag the orange timeline to seek in the current song.";break;
            case 10:title="Now playing";about="Track title and position from the current on-watch player.";break;
            default:title="Pearl help";about="Move the helper Pearl onto a playback control, or into the triangle for voice.";
        }
        final int selected=action;
        AlertDialog.Builder dialog=new AlertDialog.Builder(host)
            .setTitle(title).setMessage(about)
            .setNegativeButton("CLOSE",null);
        if(selected>=1 && selected<=8){
            dialog.setPositiveButton("ACTIVATE",(d,which)->{
                switch(selected){
                    case 1:controls.volume(-1);break;
                    case 2:controls.previous();break;
                    case 3:controls.playPause();break;
                    case 4:controls.next();break;
                    case 5:controls.volume(1);break;
                    case 6:controls.library();break;
                    case 7:controls.lyrics();break;
                    case 8:controls.shuffle();break;
                    default:break;
                }
            });
        }
        dialog.show();
    }

    void requestVoice(){
        new AlertDialog.Builder(host)
            .setTitle("PTIV • VOICE MODE")
            .setMessage("Open the watch's available speech recognizer for one command? No continuous listening. Offline speech availability depends on the installed recognizer; no audio is stored by this player.\n\nTry: PLAY, PAUSE, NEXT, PREVIOUS, VOLUME UP, VOLUME DOWN, SONGS, LYRICS, SHUFFLE, PERFORMER, SERVICES, HELP.")
            .setPositiveButton("START VOICE",(d,which)->startVoice())
            .setNegativeButton("CANCEL",null).show();
    }

    private void startVoice(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"P.T. Architech voice command");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        try{
            host.startActivityForResult(i,SPEECH_REQUEST);
        }catch(ActivityNotFoundException | SecurityException e){
            new AlertDialog.Builder(host)
                .setTitle("VOICE NOT INSTALLED")
                .setMessage("The watch has no compatible speech recognition activity. Offline music remains usable without voice. Use the triangle's CONTROL menu until a speech recognizer is available.")
                .setPositiveButton("CONTROLS",(d,w)->showControls())
                .setNegativeButton("CLOSE",null).show();
        }
    }

    void onVoiceResult(Intent data){
        if(data==null)return;
        ArrayList<String> choices=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(choices==null||choices.isEmpty())return;
        String words=choices.get(0).toLowerCase(Locale.ROOT).trim();
        if(words.contains("volume up")||words.contains("louder"))controls.volume(1);
        else if(words.contains("volume down")||words.contains("quieter"))controls.volume(-1);
        else if(words.contains("previous")||words.contains("back track"))controls.previous();
        else if(words.contains("next"))controls.next();
        else if(words.contains("pause")||words.contains("stop music"))controls.playPause();
        else if(words.equals("play")||words.contains("play music"))controls.playPause();
        else if(words.contains("song")||words.contains("library"))controls.library();
        else if(words.contains("lyric"))controls.lyrics();
        else if(words.contains("shuffle"))controls.shuffle();
        else if(words.contains("performer"))controls.performer();
        else if(words.contains("service")||words.contains("spotify"))controls.services();
        else if(words.contains("help")||words.contains("info"))showHelp();
        else info("Command not recognized: "+words+". No action was taken. Try 'HELP' for supported commands.");
    }

    private void info(String msg){
        new AlertDialog.Builder(host).setTitle("P.T. ARCHITECH • PEARL")
            .setMessage(msg).setPositiveButton("OK",null).show();
    }
}
