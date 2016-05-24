package com.cml.imitate.netease.service;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.cml.imitate.netease.db.bean.Song;
import com.cml.imitate.netease.notification.MusicNotification;
import com.cml.imitate.netease.notification.NeteaseNotification;
import com.cml.imitate.netease.receiver.MusicServiceReceiver;
import com.cml.imitate.netease.receiver.bean.PlayMusicBean;
import com.cml.imitate.netease.utils.pref.PrefUtil;
import com.cml.imitate.netease.utils.songlist.SongListUtil;

/**
 * Created by cmlBeliever on 2016/4/22.
 * 播放音乐服务
 */
public class MusicService extends Service {

    private static final String TAG = MusicService.class.getSimpleName();

    private final Messenger messenger = new Messenger(new MusicHandler());
    private Messenger playMessenger;
    private static final int NOTIFICATION_ID = 1001;

    private NeteaseNotification<PlayMusicBean> notification;
    private MusicServiceReceiver musicServiceReceiver;
    private PlayMusicBean playMusicBean;
    private MusicPlayerClient musicPlayerClient;//音乐播放控制器

    /**
     * 音乐播放信息回调处理
     */
    private MusicPlayerClient.MusicCallback musicCallback = new MusicPlayerClient.MusicCallback() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            sendMsg(ControlCode.COMPLETED);
        }

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            sendMsg(ControlCode.ERROR);
            return false;
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            sendMsg(ControlCode.PLAY);
        }

        private void sendMsg(int what) {
            Message msg = Message.obtain();
            if (null != playMessenger) {
                try {
                    msg.what = what;
                    playMessenger.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        //播放音乐notification
        notification = new MusicNotification(this, NOTIFICATION_ID);
        playMusicBean = new PlayMusicBean(NOTIFICATION_ID, "url", "name", "author", false, true);

        //
        musicPlayerClient = new MusicPlayerClient(this, musicCallback);

        //注册广播监听
        musicServiceReceiver = new MusicServiceReceiver(notification);
        registerReceiver(musicServiceReceiver, new IntentFilter(com.cml.imitate.netease.receiver.MusicServiceReceiver.ACTION));
    }

    @Override
    public void onDestroy() {
        //通知栏信息监听去除
        if (null != musicServiceReceiver) {
            unregisterReceiver(musicServiceReceiver);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //TODO 1 显示notification
        //如果允许显示通知栏，添加通知栏
        if (PrefUtil.getIsFrontService()) {
            notification.initOrUpdate(playMusicBean);
            notification.show();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private class MusicHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Messenger target = msg.replyTo;
            Message replayMsg = Message.obtain();
            switch (msg.what) {
                case ControlCode.PLAY://音乐播放
                    playMessenger = msg.replyTo;
                    musicPlayerClient.play((Uri) msg.obj);
                    break;
                case ControlCode.PLAY_INDEX://根据列表id播放
                    playMessenger = msg.replyTo;
                    Song song = SongListUtil.getInstance().getCurrent();
                    replayMsg.what = ControlCode.PLAY_INDEX;
                    if (null == song) {
                        replayMsg.arg1 = ControlCode.FAIL;
                    } else {
                        replayMsg.arg1 = ControlCode.OK;
                        musicPlayerClient.play(Uri.parse(song.url));
                    }
                    try {
                        target.send(replayMsg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    public static interface ControlCode {
        int OK = 1000;
        int FAIL = 1001;
        int STOP = 1;
        int PLAY = 2;
        int PLAY_INDEX = 20;//根据音乐id播放
        int LOOP = 3;
        int EXIT = 4;
        int NEXT = 5;
        int LIST = 6;
        int COMPLETED = 7;
        int ERROR = 8;
    }

}
