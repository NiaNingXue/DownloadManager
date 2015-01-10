package com.nianing.downloadmanager.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.nianing.downloadmanager.DownloadInfo;
import com.nianing.downloadmanager.DownloadManager;
import com.nianing.downloadmanager.Downloads;
import com.nianing.downloadmanager.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class MainActivity extends Activity implements View.OnClickListener {

    private ListView mListView;
    private DownLoadAdapter mAdapter;
    private TextView tvAddTask;
    private DownloadManager mDownloadManager;
    private List<DownloadInfo> mList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        IntentFilter downIntentFilter = new IntentFilter();
        downIntentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, downIntentFilter);
        mDownloadManager = DownloadManager.getInstance(getApplication());
        mListView = (ListView) findViewById(R.id.listview);
        mAdapter = new DownLoadAdapter();
        mList = new ArrayList<DownloadInfo>();
        mListView.setAdapter(mAdapter);
        tvAddTask = (TextView) findViewById(R.id.add);
        tvAddTask.setOnClickListener(this);
    }

    BroadcastReceiver mReceiver =  new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            HashMap<Long, DownloadInfo> map= (HashMap<Long, DownloadInfo>) intent.getSerializableExtra("Download");
            Set<Long> keys = map.keySet();
            mList.clear();
            for(Long id:keys){
                DownloadInfo info = map.get(id);
                mList.add(info);
            }
            mAdapter.notifyDataSetChanged();
        }
    };

    private  final int MAX = 8;
    private  int cur = 0;

    @Override
    public void onClick(View v) {
        if(cur<=MAX){
            Uri uri = Uri.parse("http://cdn1.lbesec.com/products/release/201412/LBE_Security_Rel_5.4.8158_A1.apk");
            DownloadManager.Request request = new DownloadManager.Request(uri);
            String path = "/storage/sdcard0/LBE"+cur;
            request.setDestinationPath(path);
            request.addRequestHeader("aa","bbb");
            cur++;
            mDownloadManager.enqueue(request);
        }
    }


    class DownLoadAdapter extends BaseAdapter{

        @Override
        public int getCount() {
            if(mList==null)
                return 0;
            return mList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final DownloadInfo info =  mList.get(position);
            View item = getLayoutInflater().inflate(R.layout.item, null);
            TextView tvName = (TextView) item.findViewById(R.id.tv_name);
            TextView tvPercent = (TextView) item.findViewById(R.id.tv_percent);
            final TextView tvPause = (TextView) item.findViewById(R.id.tv_pause);
            TextView tvDelete = (TextView) item.findViewById(R.id.tv_delete);
            if(info.mStatus== Downloads.Columns.STATUS_RUNNING){
                tvPause.setText("暂停");
            }else{
                tvPause.setText("开始");
            }
            tvPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(info.mStatus==Downloads.Columns.STATUS_RUNNING){
                        mDownloadManager.stop(info.mId);
                    }else{
                        mDownloadManager.start(info.mId);
                    }
                }
            });
            tvDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDownloadManager.remove(info.mId);
                }
            });
            tvName.setText(info.mDestination);
            tvPercent.setText((info.mCurrentBytes*100/info.mTotalBytes)+"");
            return item;
        }
    }
}
