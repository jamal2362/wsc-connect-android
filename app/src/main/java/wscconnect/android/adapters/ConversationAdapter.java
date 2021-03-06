package wscconnect.android.adapters;

import android.graphics.Typeface;
import android.os.Handler;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import wscconnect.android.GlideApp;
import wscconnect.android.R;
import wscconnect.android.activities.AppActivity;
import wscconnect.android.fragments.myApps.appOptions.AppConversationsFragment;
import wscconnect.android.models.ConversationModel;

/**
 * @author Christopher Walz
 * @copyright 2017-2018 Christopher Walz
 * @license GNU General Public License v3.0 <https://opensource.org/licenses/LGPL-3.0>
 */

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.MyViewHolder> {
    private final AppConversationsFragment fragment;
    private final AppActivity activity;
    private final List<ConversationModel> conversationList;

    public ConversationAdapter(AppActivity activity, AppConversationsFragment fragment, List<ConversationModel> conversationList) {
        this.activity = activity;
        this.fragment = fragment;
        this.conversationList = conversationList;
    }

    @NotNull
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_conversation, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final MyViewHolder holder, int position) {
        ConversationModel conversation = conversationList.get(position);

        holder.title.setText(conversation.getTitle());
        holder.time.setText(conversation.getRelativeTime(activity));
        holder.participants.setText(conversation.getParticipants());
        GlideApp.with(activity).load(conversation.getAvatar()).error(R.drawable.ic_person_black_50dp).circleCrop().into(holder.avatar);

        if (conversation.isNew()) {
            holder.title.setTypeface(null, Typeface.BOLD);
        } else {
            holder.title.setTypeface(null, Typeface.NORMAL);
        }
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView title, time, participants;
        public ImageView avatar;

        public MyViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.list_conversation_title);
            time = view.findViewById(R.id.list_conversation_time);
            participants = view.findViewById(R.id.list_conversation_participants);
            avatar = view.findViewById(R.id.list_conversation_avatar);

            view.setOnClickListener(view1 -> loadConversationMessages());
        }

        private void loadConversationMessages() {
            int position = getAdapterPosition();
            fragment.refreshViewRefreshing(true);

            // RecyclerView.NO_POSITION is returned, if notifyDataSetChanged() has been called just now
            if (position != RecyclerView.NO_POSITION) {
                ConversationModel conversation = conversationList.get(position);
                fragment.getConversationMessages(conversation, AppConversationsFragment.LIMIT, 0, null);
            } else {
                // wait a short time and try again.
                new Handler().postDelayed(this::loadConversationMessages, 200);
            }
        }
    }
}
