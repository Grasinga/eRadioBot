import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

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
 * <a href="https://github.com/sedmelluq/lavaplayer" target="_blank">lavaplayer</a>
 */
public class eRadio extends  ListenerAdapter{

    /**
     * Starts the bot with the given arguments (from command line or bot.properties).
     *
     * @param args Given arguments from command line.
     */
    public static void main(String[] args) {
        String token = "";
        String station = "";
        String voiceChannel = "General";

        // Initialize bot via the command line.
        if(args.length >= 3) {
            token = args[0];
            station = args[1];
            voiceChannel = args[2];
        }
        try {
            if(token.equals("") && station.equals("")) {
                BufferedReader br = Files.newBufferedReader(Paths.get("./bot.properties"));

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

            new JDABuilder(AccountType.BOT)
                    .setBulkDeleteSplittingEnabled(false)
                    .setToken(token)
                    .addListener(new eRadio(station, voiceChannel))
                    .buildBlocking();
        }
        catch (IllegalArgumentException e) {
            System.out.println("The config was not populated. Please make sure all arguments were given.");
        }
        catch (LoginException e) {
            System.out.println("The provided bot token was incorrect. Please provide a valid token.");
        }
        catch (InterruptedException | RateLimitedException e) {
            System.out.println("A thread interruption occurred. Check Stack Trace below for source.");
            e.printStackTrace();
        }
        catch (FileNotFoundException e) {
            System.out.println("Could not find Bot Token file!");
        }
        catch (IOException e) {
            System.out.println("Could not read Bot Token file!");
        }
        catch (Exception e) {
            System.out.println("A general exception was caught. Exception: " + e.getCause());
        }
    }

    /**
     * Radio station's URL.
     */
    private String stationURL = "";

    /**
     * The audio form of the station.
     */
    private AudioTrack station = null;

    /**
     * VoiceChannel that the bot will play in. Has a default value of "General" (from main()).
     */
    private String voiceChannel = "";

    private final AudioPlayerManager playerManager;

    /**
     * Initializes the bot with the station and VoiceChannel.
     *
     * @param stationURL Internet Radio Station URL
     * @param voiceChannel Discord VoiceChannel the bot will play in.
     */
    private eRadio(String stationURL, String voiceChannel) {
        this.stationURL = stationURL;
        this.voiceChannel = voiceChannel;

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    /**
     * Joins the {@link #voiceChannel} specified by the bot.properties file if possible.
     *
     * @param guild The guild in which the {@link #voiceChannel} resides.
     * @param channel Used to send a message saying the {@link #voiceChannel} was not found if it could not connect.
     */
    private void joinVoice(Guild guild, TextChannel channel) {
        //Scans through the VoiceChannels in this Guild, looking for one with a case-insensitive matching name.
        VoiceChannel voice = guild.getVoiceChannels().stream().filter(
                vChan -> vChan.getName().equalsIgnoreCase(voiceChannel))
                .findFirst().orElse(null);
        if (voice == null)
        {
            channel.sendMessage("There isn't a VoiceChannel called: '" + voiceChannel + "'! Please create one to use this bot!");
            return;
        }
        try {
            guild.getAudioManager().openAudioConnection(voice);
        }catch (Exception e){e.printStackTrace();}
    }

    /**
     * Handles getting and executing commands.
     *
     * @param event Carries the {@link Message} which contains the command.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Guild guild = event.getGuild();

        if(guild != null) {
            String command = event.getMessage().getContent();
            switch (command.toLowerCase()) {
                case "-join":
                    joinVoice(event.getGuild(), event.getTextChannel());
                    break;
                case "-leave":
                    event.getGuild().getAudioManager().closeAudioConnection();
                    break;
                case "-play":
                    loadAndPlay(event.getTextChannel());
                    break;
                case "-nowplaying":
                    nowPlaying(event.getTextChannel());
                    break;
                case "-stop":
                    stopPlayer(event.getTextChannel());
                    break;
                case "-help":
                    sendCommands(event.getAuthor());
                    break;
            }
        }

        super.onMessageReceived(event);
    }

    /**
     * Loads the station and then calls {@link #play(Guild, TextChannel, GuildMusicManager, AudioTrack)}.
     *
     * @param channel {@link TextChannel} to send messages to.
     */
    private void loadAndPlay(final TextChannel channel) {

        if (station == null) {
            GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
            playerManager.loadItemOrdered(musicManager, stationURL, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    play(channel.getGuild(), channel, musicManager, track);
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    // Not needed.
                }

                @Override
                public void noMatches() {
                    channel.sendMessage(stationURL + " could not be found!").queue();
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    channel.sendMessage("Could not play: " + exception.getMessage()).queue();
                    exception.printStackTrace();
                }
            });
        }
        else
            channel.sendMessage("eRadio is already playing!").queue();
    }

    /**
     * Creates the {@link GuildMusicManager} to handle the music being played.
     *
     * @param guild The {@link Guild} the manager pertains to.
     * @return The {@link GuildMusicManager}
     */
    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        GuildMusicManager musicManager = new GuildMusicManager(playerManager);

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    /**
     * Joins the {@link VoiceChannel} specified by {@link #voiceChannel}, then queues the {@link #station}, and finally
     * sends a message confirming the start or continued play of the radio.
     *
     * @param guild Used to get the {@link VoiceChannel}s available.
     * @param channel {@link TextChannel} to send a message confirming the start or continued play of the radio.
     * @param musicManager The {@link GuildMusicManager} used to queue the selected
     * @param radioStation The radio station to be queued.
     */
    private void play(Guild guild, TextChannel channel, GuildMusicManager musicManager, AudioTrack radioStation) {
        joinVoice(guild, channel);

        station = radioStation;

        musicManager.scheduler.queue(radioStation);
        nowPlaying(channel);
    }

    /**
     * Sends the currently playing song's info to channel as a message.
     *
     * @param channel Used to get the TextChannel to send the message to.
     */
    private void nowPlaying(TextChannel channel) {
        if (channel.getGuild().getAudioManager().getSendingHandler() != null && station != null) {
            if(!InternetRadioParser.getStationName(stationURL).equalsIgnoreCase("Unknown")) {
                if (!InternetRadioParser.getCurrentSongArtist(stationURL).equalsIgnoreCase("Unknown") ||
                        !InternetRadioParser.getCurrentSong(stationURL).equalsIgnoreCase("Unknown"))
                    channel.sendMessage(
                                "***" + InternetRadioParser.getStationName(stationURL) + "***\n" +
                                "**Artist:** " + InternetRadioParser.getCurrentSongArtist(stationURL) + "\n" +
                                "**Song:** " + InternetRadioParser.getCurrentSong(stationURL)
                    ).queue();
                else
                    channel.sendMessage(
                            "**Radio Station:** " + InternetRadioParser.getStationName(stationURL) + "\n" +
                                    "**Song:** " + InternetRadioParser.getCurrentSongInfo(stationURL)
                    ).queue();
            }
        }
        else
            channel.sendMessage("eRadio is not currently playing anything!").queue();
    }

    /**
     * Stops the player if playing and sends a confirmation message. Notifies the command user if it is already stopped.
     *
     * @param channel {@link TextChannel} to send messages to.
     */
    private void stopPlayer(TextChannel channel) {
        if(station == null)
            channel.sendMessage("eRadio is already stopped!").queue();
        else {
            channel.getGuild().getAudioManager().setSendingHandler(null);
            station = null;
            channel.sendMessage("eRadio has stopped.").queue();
        }
    }

    /**
     * Sends a list of commands the bot has available to the command user.
     *
     * @param user The {@link User} that entered the command. Used to get their {@link PrivateChannel}.
     */
    private void sendCommands(User user) {
        PrivateChannel pm = user.openPrivateChannel().complete();
        pm.sendMessage(
                "__**Commands:**__\n" +
                        "```\n" +
                        "-join // Joins the VoiceChannel from the bot.properties file if possible.\n" +
                        "-leave // Leaves the current VoiceChannel if in one.\n" +
                        "-play // Starts playback.\n" +
                        "-nowplaying // Gets the current song's info if possible.\n" +
                        "-stop // Stops playback.\n" +
                        "-help // Messages the user a list of commands.\n" +
                        "```"
        ).queue();
    }
}