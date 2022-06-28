package com.my.mediaplayer

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.my.mediaplayer.ui.main.NowPlayingFragment


class MusicService: Service(), AudioManager.OnAudioFocusChangeListener {
    private var myBinder = MyBinder()
    var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession : MediaSessionCompat
    private lateinit var runnable: Runnable
    private lateinit var wakeLock: PowerManager.WakeLock
    lateinit var audioManager: AudioManager
    private var startId :Int = 0



    override fun onBind(intent: Intent?): IBinder {
        mediaSession = MediaSessionCompat(baseContext, "My Music")
        return myBinder
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onTaskRemoved(rootIntent: Intent?) {

        stopForeground(false)
        super.onTaskRemoved(rootIntent)
    }


    inner class MyBinder: Binder(){
        fun currentService(): MusicService {
            return this@MusicService
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    override fun onCreate() {
        super.onCreate()


        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakelockTag123").apply {
                acquire()
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)

    fun showNotification(playPauseBtn: Int){

        val ns = NOTIFICATION_SERVICE
        val notificationManager = getSystemService(ns) as NotificationManager
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel("my_service", "My Background Service")
            } else {
                ApplicationClass.CHANNEL_ID
            }
        val intent = Intent(baseContext, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, intent,PendingIntent.FLAG_MUTABLE)

        val prevIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.PREVIOUS)
        val prevPendingIntent = PendingIntent.getBroadcast(baseContext, 0, prevIntent, PendingIntent.FLAG_MUTABLE)

        val playIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.PLAY)
        val playPendingIntent = PendingIntent.getBroadcast(baseContext, 0, playIntent, PendingIntent.FLAG_MUTABLE)

        val nextIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.NEXT)
        val nextPendingIntent = PendingIntent.getBroadcast(baseContext, 0, nextIntent, PendingIntent.FLAG_MUTABLE)

        val exitIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.EXIT)
        val exitPendingIntent = PendingIntent.getBroadcast(baseContext, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE)

        val imgArt = getImgArt(PlayerActivity.musicListPA[PlayerActivity.songPosition].path)
        val image = if(imgArt != null){
            BitmapFactory.decodeByteArray(imgArt, 0, imgArt.size)
        }else{
            BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_slash_screen)
        }
        val notification = NotificationCompat.Builder(baseContext, channelId)
            .setContentIntent(contentIntent)
            .setContentTitle(PlayerActivity.musicListPA[PlayerActivity.songPosition].title)
            .setContentText(PlayerActivity.musicListPA[PlayerActivity.songPosition].artist)
            .setSmallIcon(R.drawable.music_icon)
            .setLargeIcon(image)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
//            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setPriority(NotificationManager.IMPORTANCE_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(R.drawable.previous_icon, "Previous", prevPendingIntent)
            .addAction(playPauseBtn, "Play", playPendingIntent)
            .addAction(R.drawable.next_icon, "Next", nextPendingIntent)
            .addAction(R.drawable.exit_icon, "Exit", exitPendingIntent)
            .build()



        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            val playbackSpeed = if(PlayerActivity.isPlaying) 1F else 0F
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer!!.duration.toLong())
                .build())
            val playBackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer!!.currentPosition.toLong(), playbackSpeed)
                .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                .build()
            mediaSession.setPlaybackState(playBackState)
            mediaSession.setCallback(object: MediaSessionCompat.Callback(){
                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    mediaPlayer!!.seekTo(pos.toInt())
                    val playBackStateNew = PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer!!.currentPosition.toLong(), playbackSpeed)
                        .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                        .build()
                    mediaSession.setPlaybackState(playBackStateNew)
                }


            })
        }

        startForeground(101, notification)




    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }
    fun createMediaPlayer(){
        try {
            if (mediaPlayer == null) mediaPlayer = MediaPlayer()
            mediaPlayer!!.reset()
            mediaPlayer!!.setDataSource(PlayerActivity.musicListPA[PlayerActivity.songPosition].path)
            mediaPlayer!!.prepare()
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                showNotification(R.drawable.pause_icon)
            }
            PlayerActivity.binding.tvSeekBarStart.text = formatDuration(mediaPlayer!!.currentPosition.toLong())
            PlayerActivity.binding.tvSeekBarEnd.text = formatDuration(mediaPlayer!!.duration.toLong())
            PlayerActivity.binding.seekBarPA.progress = 0
            PlayerActivity.binding.seekBarPA.max = mediaPlayer!!.duration
            PlayerActivity.nowPlayingId = PlayerActivity.musicListPA[PlayerActivity.songPosition].id
            PlayerActivity.loudnessEnhancer = LoudnessEnhancer(mediaPlayer!!.audioSessionId)
            PlayerActivity.loudnessEnhancer.enabled = true
        }catch (e: Exception){return}
    }

    fun seekBarSetup(){
        runnable = Runnable {
            PlayerActivity.binding.tvSeekBarStart.text = formatDuration(mediaPlayer!!.currentPosition.toLong())
            PlayerActivity.binding.seekBarPA.progress = mediaPlayer!!.currentPosition
            Handler(Looper.getMainLooper()).postDelayed(runnable, 200)
        }
        Handler(Looper.getMainLooper()).postDelayed(runnable, 0)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if(focusChange <= 0){

            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
            NowPlayingFragment.binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
            PlayerActivity.isPlaying = false
            mediaPlayer!!.pause()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                showNotification(R.drawable.play_icon)
            }

        }
    }




    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startId = startId


//        if (intent?.action != null && intent.action.equals(
//               ApplicationClass.ACTION_STOP_FOREGROUND, ignoreCase = true)) {
//            stopForeground(false)
//            stopSelf()
//        }


        return Service.START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onDestroy() {

        val broadcastIntent = Intent()
        broadcastIntent.action = "restartService"
        broadcastIntent.setClass(baseContext, NotificationReceiver::class.java)
        sendBroadcast(broadcastIntent)

        super.onDestroy()
    }

//    private fun ensureServiceStaysRunning() {
//        val restartAlarmInterval = 60 * 1000
//        val resetAlarmTimer = 30 * 1000L
//        val restartIntent = Intent(this, NotificationReceiver::class.java)
//        restartIntent.action = "RestartedViaAlarm"
//        restartIntent.flags = Intent.FLAG_RECEIVER_FOREGROUND
//        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        val restartServiceHandler = @SuppressLint("HandlerLeak")
//        object : Handler() {
//            override fun handleMessage(msg: Message) {
//                val pendingIntent = PendingIntent.getBroadcast(applicationContext, 87, restartIntent, PendingIntent.FLAG_MUTABLE)
//                val timer = System.currentTimeMillis() + restartAlarmInterval
//                val sdkInt = Build.VERSION.SDK_INT
//                if (sdkInt < Build.VERSION_CODES.KITKAT)
//                    alarmMgr.set(AlarmManager.RTC_WAKEUP, timer, pendingIntent)
//                else if (Build.VERSION_CODES.KITKAT <= sdkInt && sdkInt < Build.VERSION_CODES.M)
//                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, timer, pendingIntent)
//                else if (sdkInt >= Build.VERSION_CODES.M) {
//                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer, pendingIntent)
//                }
//                sendEmptyMessageDelayed(0, resetAlarmTimer)
//                stopSelf()
//            }
//        }
//        restartServiceHandler.sendEmptyMessageDelayed(0, 0)
//    }



}