package wscconnect.android.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import wscconnect.android.GlideApp;
import wscconnect.android.HeaderViewHolder;
import wscconnect.android.R;
import wscconnect.android.Utils;
import wscconnect.android.ViewHolder;
import wscconnect.android.activities.AppActivity;
import wscconnect.android.callbacks.RetroCallback;
import wscconnect.android.fragments.myApps.appOptions.AppConversationsFragment;
import wscconnect.android.models.AccessTokenModel;
import wscconnect.android.models.ConversationMessageModel;
import wscconnect.android.models.ConversationModel;

/**
 * @author Christopher Walz
 * @copyright 2017-2018 Christopher Walz
 * @license GNU General Public License v3.0 <https://opensource.org/licenses/LGPL-3.0>
 */

public class ConversationMessageAdapter extends RecyclerView.Adapter<ViewHolder> {
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_MESSAGE = 2;
    private static final int TYPE_FORM = 3;
    private static final int TYPE_LOAD_MORE = 4;
    private final AppConversationsFragment fragment;
    private AccessTokenModel token;
    private ConversationModel conversation;
    private final AppActivity activity;
    private final List<ConversationMessageModel> messageList;
    private boolean conversationMessagesAutoLoad = false;

    public ConversationMessageAdapter(AppActivity activity, AppConversationsFragment fragment, List<ConversationMessageModel> messageList, AccessTokenModel token) {
        this.activity = activity;
        this.fragment = fragment;
        this.messageList = messageList;
        this.token = token;
    }

    public void setConversation(ConversationModel conversation) {
        conversationMessagesAutoLoad = false;
        this.conversation = conversation;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        } else if (getItemCount() == position + 1) {
            return TYPE_FORM;
        } else if (getItemCount() == position + 2 && !conversationMessagesAutoLoad) {
            return TYPE_LOAD_MORE;
        } else {
            return TYPE_MESSAGE;
        }
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        ViewHolder v = null;

        switch (viewType) {
            case TYPE_HEADER:
                v = new HeaderViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_header, parent, false));
                break;
            case TYPE_MESSAGE:
                v = new MessageViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_generic_message, parent, false));
                break;
            case TYPE_FORM:
                v = new FormViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_generic_message_form, parent, false));
                break;
            case TYPE_LOAD_MORE:
                v = new LoadMoreViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_generic_message_load_more, parent, false));
                break;
        }

        assert v != null;
        return v;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case TYPE_HEADER:
                HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;

                headerViewHolder.title.setText(conversation.getTitle());
                headerViewHolder.subtitle.setText(activity.getString(R.string.list_conversation_message_header_participants, conversation.getParticipants()));
                break;
            case TYPE_MESSAGE:
                MessageViewHolder messageViewHolder = (MessageViewHolder) holder;
                position = getActualPosition(position);

                ConversationMessageModel message = messageList.get(position);
                messageViewHolder.message.setText(Utils.fromHtml(message.getMessage()));
                messageViewHolder.time.setText(message.getRelativeTime(activity));
                messageViewHolder.username.setText(message.getUsername());
                GlideApp.with(activity).load(message.getAvatar()).error(R.drawable.ic_person_black_50dp).circleCrop().into(messageViewHolder.avatar);

                // remove bottom border for last item
                if (position + 1 == messageList.size()) {
                    messageViewHolder.content.setBackground(null);
                } else {
                    messageViewHolder.content.setBackgroundResource(R.drawable.border_bottom);
                }
                break;
            case TYPE_FORM:
                FormViewHolder formViewHolder = (FormViewHolder) holder;
                Utils.setError(formViewHolder.text, null);

                if (conversation.isClosed()) {
                    formViewHolder.text.setVisibility(View.GONE);
                    formViewHolder.submit.setText(R.string.list_conversation_message_form_closed);
                } else {
                    formViewHolder.text.setVisibility(View.VISIBLE);
                    formViewHolder.submit.setText(R.string.submit);
                }
                break;
        }
    }

    private int getActualPosition(int position) {
        return position - 1 ;
    }

    @Override
    public int getItemCount() {
        int add = 2;
        if (!conversationMessagesAutoLoad) {
            add++;
        }
        return messageList.size() + add;
    }

    public boolean isConversationMessagesAutoLoad() {
        return conversationMessagesAutoLoad;
    }
    public void setConversationMessagesAutoLoad(boolean conversationMessagesAutoLoad) {
        this.conversationMessagesAutoLoad = conversationMessagesAutoLoad;
    }

    public class FormViewHolder extends ViewHolder {
        public EditText text;
        public Button submit;

        public FormViewHolder(View view) {
            super(view);
            text = view.findViewById(R.id.list_generic_message_form_text);
            submit = view.findViewById(R.id.list_generic_message_form_submit);

            submit.setOnClickListener(view1 -> {
                if (!submit.isEnabled()) {
                    return;
                }

                String separator = System.getProperty("line.separator");
                assert separator != null;
                String message = text.getText().toString().trim().replaceAll(separator, "<br>");

                if (message.isEmpty()) {
                    Utils.setError(activity, text);
                    return;
                }

                final ProgressBar progressBar = Utils.showProgressView(activity, submit, android.R.attr.progressBarStyle);
                fragment.addConversationMessage(conversation.getConversationID(), message, (json, success) -> {
                    if (success) {
                        text.setText(null);
                        Utils.hideKeyboard(activity);
                    } else if (json != null) {
                        try {
                            String message1 = json.getString("message");
                            switch (message1) {
                                case "censorship":
                                    JSONArray words = json.getJSONArray("returnValues");
                                    String censoredWords = words.join(", ");
                                    Utils.setError(text, activity.getString(R.string.list_conversation_message_form_error_censorship, censoredWords));
                                    break;

                                case "length":
                                    int maxLength = json.getInt("returnValues");
                                    Utils.setError(text, activity.getString(R.string.list_conversation_message_form_error_length, message1.length(), maxLength));
                                    break;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    Utils.hideProgressView(submit, progressBar);
                });
            });
        }
    }

    public class LoadMoreViewHolder extends ViewHolder {
        public LinearLayout loadMoreContainer;
        public Button loadMore;

        public LoadMoreViewHolder(View view) {
            super(view);
            loadMoreContainer = view.findViewById(R.id.list_generic_message_load_more_container);
            loadMore = view.findViewById(R.id.list_generic_message_load_more);

            loadMore.setOnClickListener(view1 -> {
                final ProgressBar progressBar = Utils.showProgressView(activity, loadMore, android.R.attr.progressBarStyle);

                fragment.onLoadMoreConversationMessages(success -> {
                    if (success) {
                        conversationMessagesAutoLoad = true;
                    } else {
                        Toast.makeText(activity, R.string.error_general, Toast.LENGTH_SHORT).show();
                    }
                    Utils.hideProgressView(loadMore, progressBar);
                });
            });
        }
    }

    public class MessageViewHolder extends ViewHolder {
        public TextView message, time, username;
        public ImageView avatar;
        public LinearLayout content;

        public MessageViewHolder(View view) {
            super(view);
            message = view.findViewById(R.id.list_conversation_message_message);
            time = view.findViewById(R.id.list_conversation_message_time);
            username = view.findViewById(R.id.list_conversation_message_username);
            avatar = view.findViewById(R.id.list_conversation_message_avatar);
            content = view.findViewById(R.id.list_conversation_message_content);

            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    final ConversationMessageModel conversationMessage = messageList.get(getActualPosition(getAdapterPosition()));
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                    builder.setItems(R.array.list_conversation_message_dialog_items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0:
                                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                    clipboard.setPrimaryClip(ClipData.newPlainText("text", conversationMessage.getMessage()));
                                    Toast.makeText(activity, R.string.list_conversation_message_dialog_copied, Toast.LENGTH_SHORT).show();
                                    break;
                                case 1:
                                    getDetailedConversationMessage();
                                    break;
                            }
                        }

                        @SuppressWarnings("deprecation")
                        private void getDetailedConversationMessage() {
                            final ProgressBar progressBar = Utils.showProgressView(activity, message, android.R.attr.progressBarStyle);
                            Utils.getAPI(activity, Utils.prepareApiUrl(token.getAppApiUrl()), token.getToken()).getConversationMessage(Utils.getApiUrlExtension(token.getAppApiUrl()), RequestBody.create(MediaType.parse("text/plain"), "getConversationMessage"), RequestBody.create(MediaType.parse("text/plain"), String.valueOf(conversationMessage.getMessageID()))).enqueue(new RetroCallback<ConversationMessageModel>(activity) {
                                @Override
                                public void onResponse(@NotNull Call<ConversationMessageModel> call, @NotNull Response<ConversationMessageModel> response) {
                                    super.onResponse(call, response);

                                    Utils.hideProgressView(message, progressBar);

                                    if (response.isSuccessful()) {
                                        WebView webView = new WebView(activity);
                                        assert response.body() != null;
                                        webView.loadData(response.body().getMessage(), "text/html; charset=UTF-8", null);

                                        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
                                        dialog.setView(webView);
                                        dialog.setPositiveButton(R.string.close, null);

                                        dialog.show();
                                    } else if (response.code() == 409) {
                                        Utils.refreshAccessToken(activity, token.getAppID(), success -> {
                                            if (success) {
                                                // refresh token
                                                token = Utils.getAccessToken(activity, token.getAppID());
                                                getDetailedConversationMessage();
                                            }
                                        });
                                    } else {
                                        RetroCallback.showRequestError(activity);
                                    }
                                }

                                @Override
                                public void onFailure(Call<ConversationMessageModel> call, @NotNull Throwable t) {
                                    super.onFailure(call, t);

                                    Utils.hideProgressView(message, progressBar);
                                }
                            });
                        }
                    });

                    builder.show();
                    return true;
                }
            });
        }
    }
}
