package net.grasinga.discord.bots;

import net.dv8tion.jda.JDABuilder;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.entities.VoiceChannel;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.dv8tion.jda.managers.AudioManager;
import net.dv8tion.jda.player.MusicPlayer;
import net.dv8tion.jda.player.Playlist;
import net.dv8tion.jda.player.source.AudioSource;
import net.grasinga.discord.libraries.InternetRadioParser;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * <a href="http://ethereal.network/" target="_blank">Ethereal Network</a>'s Radio Bot<br>
 * eRadio is a bot the plays an internet radio station on discord.<br>
 * Plays the given radio station in the given voice channel.<br>
 * Arguments can be passed in through the command line or from a bot.properties file.<br>
 * bot.properties file should have one argument per line.<br>
 * <br>
 * Arguments are as follows:<br>
 * - Bot Token<br>
 * - Radio Station URL<br>
 * - VoiceChannel<br>
 * <br>
 * Created by <a href="https://github.com/Grasinga" target="_blank">Grasinga</a> using
 * <a href="https://github.com/DV8FromTheWorld/JDA" target="_blank">JDA</a> and
 * <a href="https://github.com/DV8FromTheWorld/JDA-Player" target="_blank">JDA-Player</a>
 */
public class eRadio extends ListenerAdapter{

    /**
     * Starts the bot with the given arguments (from command line or bot.properties).
     *
     * @param args Given arguments from command line.
     */
    public static void main(String[] args) {
        String token = "";
        String station = "";
        String voiceChannel = "";
        try {
            if (args.length >= 3){
                token = args[0];
                station = args[1];
                voiceChannel = args[2];
            }
            else {
                BufferedReader br = new BufferedReader(new FileReader(new File("./bot.properties")));

                String properties = br.readLine();
                if (properties != null)
                    token = properties;

                properties = br.readLine();
                if (properties != null)
                    station = properties;

                properties = br.readLine();
                if (properties != null)
                    voiceChannel = properties;

                br.close();
            }

            new JDABuilder()
                .setBulkDeleteSplittingEnabled(false)
                .setBotToken(token)
                .addListener(new eRadio(station,voiceChannel))
                .buildBlocking();
        }
        catch (IllegalArgumentException e){
            System.out.println("The config was not populated. Please make sure all arguments were given.");
        }
        catch (LoginException e){
            System.out.println("The provided bot token was incorrect. Please provide a valid token.");
        }
        catch (InterruptedException e){
            e.printStackTrace();
        }
        catch (FileNotFoundException e){
            System.out.println("Could not find Bot Token file!");
        }
        catch (IOException e){
            System.out.println("Could not read Bot Token file!");
        }
    }

    /**
     * Radio station's URL.
     */
    private String station = "";

    /**
     * VoiceChannel that the bot will play in.
     */
    private String voiceChannel = "";

    /**
     * MusicPlayer the bot will use to play audio.
     * @see net.dv8tion.jda.player.MusicPlayer
     */
    private MusicPlayer player = new MusicPlayer();

    /**
     * Bot's current player volume.
     * @see eRadio#player
     */
    private float playerVolume = 0.50f;

    /**
     * Tells the bot if it is muted or not.
     */
    private boolean muted = false;

    /**
     * Initializes the bot with the station and VoiceChannel.
     *
     * @param station Internet Radio Station URL
     * @param voiceChannel Discord VoiceChannel the bot will play in.
     */
    private eRadio(String station, String voiceChannel) {
        this.station = station;
        this.voiceChannel = voiceChannel;
    }

    /**
     * Gets commands entered by users.
     *
     * @param event The command's message event.
     */
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        String command = event.getMessage().getContent();
        switch (command.toLowerCase()){
            case "-join":
                joinVoice(event.getGuild(),event.getChannel(),voiceChannel);
                break;
            case "-leave":
                event.getGuild().getAudioManager().closeAudioConnection();
                break;
            case "-play":
                resumePlay(player,event.getChannel());
                break;
            case "-nowplaying":
                nowPlaying(player,event.getChannel());
                break;
            case "-stop":
                stopPlayer(player,event.getChannel());
                break;
            case "-volume":
                if(command.length() > ("-volume").length())
                    setVolume(player,event.getChannel(),command);
                else
                    getVolume(event.getChannel());
                break;
            case "-mute":
                mutePlayer(player,event.getChannel());
                break;
            case "-unmute":
                unmutePlayer(player,event.getChannel());
                break;
            case "-help":
                sendCommands(event.getAuthor());
                break;
        }
    }

    /**
     * Sets the MusicPlayer's SendingHandler.
     *
     * @param audioManager Used for setting the MusicPlayer's SendingHandler.
     * @see net.dv8tion.jda.player.MusicPlayer
     */
    private void setSendingHandler(AudioManager audioManager){
        if (audioManager.getSendingHandler() == null){
            player.setVolume(playerVolume);
            audioManager.setSendingHandler(player);
        }
        else
            player = (MusicPlayer) audioManager.getSendingHandler();
    }

    /**
     * Function runs after the '-join' command is entered.
     *
     * Places the bot in the VoiceChannel specified by chanName argument if possible.
     * If the VoiceChannel cannot be joined because it doesn't exist, a message is sent to channel
     * to notify the user of the command.
     *
     * @param guild Used to get the current guild where the Exception occurred.
     * @param channel Used to get the channel of the command that caused the Exception.
     * @param chanName VoiceChannel to join.
     */
    private void joinVoice(Guild guild, TextChannel channel, String chanName) {
        //Scans through the VoiceChannels in this Guild, looking for one with a case-insensitive matching name.
        VoiceChannel voiceChannel = guild.getVoiceChannels().stream().filter(
                vChan -> vChan.getName().equalsIgnoreCase(chanName))
                .findFirst().orElse(null);
        if (voiceChannel == null)
        {
            channel.sendMessage("There isn't a VoiceChannel called: '" + chanName + "'! Please create one to use this bot!");
            return;
        }
        try {
            guild.getAudioManager().openAudioConnection(voiceChannel);
        }catch (Exception e){e.printStackTrace();}
    }

    /**
     * Resumes playback if a MusicPlayer exists and contains music.
     *
     * @param player Used to get the current MusicPlayer.
     * @param channel Used to get the TextChannel the command was entered.
     */
    private void resumePlay(MusicPlayer player, TextChannel channel){
        if (player.isPlaying())
            channel.sendMessage("Player is already playing!");
        else
        {
            try {
                setSendingHandler(channel.getGuild().getAudioManager());

                Playlist playlist = Playlist.getPlaylist(station);
                List<AudioSource> sources = new LinkedList<>(playlist.getSources());
                player.getAudioQueue().add(sources.get(0));

                if (!channel.getGuild().getAudioManager().isConnected())
                    joinVoice(channel.getGuild(), channel, voiceChannel);

                player.play();

                channel.sendMessage("__**Player has started**__");
                nowPlaying(player, channel);
            }catch (Exception e){System.out.println("An error occurred when trying to load the radio!"); e.printStackTrace();}
        }
    }

    /**
     * Sends the currently playing song's info to channel as a message.
     *
     * @param player Used to get the current MusicPlayer.
     * @param channel Used to get the TextChannel to send the message to.
     */
    private void nowPlaying(MusicPlayer player, TextChannel channel) {
        if (player.isPlaying())
            channel.sendMessage(
                    "**Playing:** " + InternetRadioParser.getStationName(player.getCurrentAudioSource().getSource()) + "\n" +
                            "**Current Song:** " + InternetRadioParser.getCurrentSong(player.getCurrentAudioSource().getSource()));
        else
            channel.sendMessage("The player is not currently playing anything!");
    }

    /**
     * Stops current playback completely and sends a message to channel
     * saying it has been stopped.
     *
     * @param player Used to get the current playback of MusicPlayer.
     * @param channel Used to get the TextChannel to send the message to.
     */
    private void stopPlayer(MusicPlayer player, TextChannel channel){
        if(!player.isPlaying())
            channel.sendMessage("Player has already been stopped!");
        else {
            player.stop();
            channel.sendMessage("Playback has been completely stopped.");
        }
    }

    /**
     * Gets the current volume and sends it to channel
     * as a message.
     *
     * @param channel Used to get the TextChannel to send the message to.
     */
    private void getVolume(TextChannel channel){channel.sendMessage("**Current Volume:** " + new DecimalFormat("#.00").format(playerVolume * 100) + "%");}

    /**
     * Sets the volume of the current MusicPlayer and then sends it to channel
     * as a message.
     *
     * @param player Used to get the current MusicPlayer.
     * @param channel Used to get the TextChannel to send the message to.
     * @param commandline Used to get the arguments of the command.
     */
    private void setVolume(MusicPlayer player, TextChannel channel, String commandline){
        try {
            if(Float.parseFloat(commandline.substring(8)) > 100)
                commandline = commandline.substring(0,8) + "100";
            if(Float.parseFloat(commandline.substring(8)) < 0)
                commandline = commandline.substring(0,8) + "0";
            playerVolume = (Float.parseFloat(commandline.substring(8)) / 100);
            player.setVolume(playerVolume);
            muted = false;
            if(commandline.equalsIgnoreCase("/volume 0") || Integer.parseInt(commandline.substring(8)) < 1)
                channel.sendMessage("**Volume Set to:** 0" + new DecimalFormat("#.00").format(playerVolume * 100) + "%");
            else
                channel.sendMessage("**Volume Set to:** " + new DecimalFormat("#.00").format(playerVolume * 100) + "%");
        }catch (Exception e){channel.sendMessage("'" + commandline.substring(8) + "' is not a valid number!");}
    }

    /**
     * Mutes the current playback and sends a message to channel
     * saying it has been muted or that it was already muted.
     *
     * @param player Used to get the current playback of MusicPlayer.
     * @param channel Used to get the TextChannel to send the message to.
     */
    private void mutePlayer(MusicPlayer player, TextChannel channel) {
        player.setVolume(0);
        if(!muted)
            channel.sendMessage("Playback muted!");
        else
            channel.sendMessage("Playback already muted!");
        muted = true;
    }

    /**
     * Unmutes the current playback and sends a message to channel
     * saying it has been unmuted or that it was already unmuted.
     *
     * @param player Used to get the current playback of MusicPlayer.
     * @param channel Used to get the TextChannel to send the message to.
     */
    private void unmutePlayer(MusicPlayer player, TextChannel channel) {
        player.setVolume(playerVolume);
        if(muted)
            channel.sendMessage("Playback un-muted!");
        else
            channel.sendMessage("Playback already un-muted!");
        muted = false;}

    /**
     * Function that gets called after the commands '/commands','/cmds', or '/help'
     * get used. Sends the user of the command a private message of all the commands usable
     * by the bot.
     *
     * @param u Used to get the user of the command and their private message channel.
     */
    private void sendCommands(User u) {
        u.getPrivateChannel().sendMessage(
                "__**Commands:**__\n" +
                        "```java\n" +
                        "!ALL COMMANDS ARE GUILD WIDE ACTIONS!\n" +
                        "-join // Joins the VoiceChannel 'Ethereal Radio' if possible.\n" +
                        "-leave // Leaves the current VoiceChannel if in one.\n" +
                        "-play // Starts or resumes playback.\n" +
                        "-nowplaying // Gets the current song's info if possible.\n" +
                        "-stop // Stops playback.\n" +
                        "-mute // Mutes playback.\n" +
                        "-unmute // Unmutes playback.\n" +
                        "-volume [Number] // Sets the volume to [Number], otherwise, shows current volume settings. \n" +
                        "-help // Messages the user a list of commands.\n" +
                        "```"
        );
    }
}
