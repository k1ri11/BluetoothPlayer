package com.example.bluetooth_player.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bluetooth_player.Models.Music;
import com.example.bluetooth_player.R;
import com.example.bluetooth_player.SongChangeListener;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.MusicViewHolder>  {

    Context context;
    List<Music> musicList;
    private itemClickListener clickListener;
    private final SongChangeListener songChangeListener;
    private int playingPosition = 0;

    public MusicAdapter(Context context, List<Music> musicList) {
        this.context = context;
        this.musicList = musicList;
        this.songChangeListener = ((SongChangeListener)context);
    }

    @NonNull
    @Override
    public MusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View musicItems = LayoutInflater.from(context).inflate(R.layout.music_item,parent, false);
        return new MusicAdapter.MusicViewHolder(musicItems);
    }

    @Override
    public void onBindViewHolder(@NonNull MusicAdapter.MusicViewHolder holder, int position) {

        Music list2 = musicList.get(position);


        if(list2.isPlaying()){
            playingPosition = position;
            holder.rootLayout.setBackgroundResource(R.drawable.round_back_blue_10);
        }
        else{
            holder.rootLayout.setBackgroundResource(R.drawable.round_back_10);
        }

        if(!list2.getDuration().equals("00:00")){
            holder.musicDuration.setVisibility(View.VISIBLE);
            String generateDuration = String.format(Locale.getDefault(), "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(Long.parseLong(list2.getDuration())),
                    TimeUnit.MILLISECONDS.toSeconds(Long.parseLong(list2.getDuration())) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(Long.parseLong(list2.getDuration()))));
            holder.musicDuration.setText(generateDuration);

        }
        else{
            holder.musicDuration.setVisibility(View.GONE);
        }

        holder.musicTitle.setText(list2.getTitle());
        holder.musicArtist.setText(list2.getArtist());


        holder.rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                musicList.get(playingPosition).setPlaying(false);
                list2.setPlaying(true);

                songChangeListener.onChanged(position);

                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return musicList.size();
    }

    public interface itemClickListener{
        void onItemClick(Music music);
    }

    public void updateList(List<Music> musics ){
        this.musicList = musics;
        notifyDataSetChanged();
    }


    public static final class MusicViewHolder extends RecyclerView.ViewHolder{

        private final TextView musicTitle, musicArtist, musicDuration;
        private final RelativeLayout rootLayout;

        public MusicViewHolder(@NonNull View itemView) {
            super(itemView);
            rootLayout = itemView.findViewById(R.id.rootLayout);
            musicTitle = itemView.findViewById(R.id.musicTitle);
            musicArtist = itemView.findViewById(R.id.musicArtist);
            musicDuration = itemView.findViewById(R.id.musicDuration);
        }
    }
}
