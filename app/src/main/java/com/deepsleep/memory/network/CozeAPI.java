package com.deepsleep.memory.network;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CozeAPI {
    private static final String TAG = "CozeAPI";

    private final String apiKey;
    private final String botId;
    private final String baseUrl = "https://api.coze.cn/v3/chat";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CozeAPI(String apiKey, String botId) {
        this.apiKey = apiKey;
        this.botId = botId;
    }

    public interface QuestionCallback {
        void onResult(@NonNull String answer, @NonNull String[] followUpQuestions);
        void onError(@NonNull String errorMessage);
    }

    public void questionService(@NonNull String questionText, @NonNull QuestionCallback callback) {
        executorService.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("bot_id", botId);
                payload.put("user_id", "memory_app_user");
                payload.put("stream", false);
                payload.put("auto_save_history", true);

                JSONArray messages = new JSONArray();
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", questionText);
                userMessage.put("content_type", "text");
                messages.put(userMessage);
                payload.put("additional_messages", messages);

                String response = sendPostRequest(baseUrl, payload.toString());
                JSONObject responseData = new JSONObject(response);

                if (!responseData.has("code") || responseData.getInt("code") != 0) {
                    String errorMsg = responseData.optString("msg", "Unknown error");
                    postError(callback, errorMsg);
                    return;
                }

                JSONObject dataObj = responseData.optJSONObject("data");
                if (dataObj == null) {
                    postError(callback, "Missing data in response");
                    return;
                }

                String chatId = dataObj.optString("id");
                String conversationId = dataObj.optString("conversation_id");

                if (chatId.isEmpty() || conversationId.isEmpty()) {
                    postError(callback, "Invalid chat or conversation ID");
                    return;
                }

                // Retrieve the answer using conversation and chat IDs
                String answerResult = retrieveAnswer(conversationId, chatId);
                if (answerResult == null) {
                    postError(callback, "Failed to retrieve answer");
                    return;
                }

                mainHandler.post(() -> callback.onResult(answerResult, new String[0]));
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    private String sendPostRequest(String requestUrl, String jsonBody) throws IOException {
        HttpURLConnection urlConnection = null;
        StringBuilder response = new StringBuilder();

        try {
            URL url = new URL(requestUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Authorization", "Bearer " + apiKey);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setDoOutput(true);

            // Send JSON body
            byte[] postData = jsonBody.getBytes("UTF-8");
            urlConnection.getOutputStream().write(postData);

            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
            } else {
                Log.e(TAG, "HTTP error code: " + responseCode);
                throw new IOException("HTTP error code: " + responseCode);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return response.toString();
    }

    private String retrieveAnswer(String conversationId, String chatId) {
        String statusUrl = baseUrl + "/retrieve?conversation_id=" + conversationId + "&chat_id=" + chatId;

        try {
            while (true) {
                String statusResponse = sendGetRequest(statusUrl);
                JSONObject statusData = new JSONObject(statusResponse);

                if (!statusData.has("data")) {
                    Log.e(TAG, "Status data missing");
                    return null;
                }

                JSONObject data = statusData.getJSONObject("data");
                String status = data.optString("status");

                if ("completed".equals(status)) {
                    String messageUrl = baseUrl + "/message/list?chat_id=" + chatId + "&conversation_id=" + conversationId;
                    String msgResponse = sendGetRequest(messageUrl);
                    JSONObject finalData = new JSONObject(msgResponse);

                    return processQuestionAnswer(finalData);
                } else {
                    Thread.sleep(1000); // Wait for 1 second before checking again
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving answer: ", e);
            return null;
        }
    }

    private String sendGetRequest(String requestUrl) throws IOException {
        HttpURLConnection urlConnection = null;
        StringBuilder response = new StringBuilder();

        try {
            URL url = new URL(requestUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Authorization", "Bearer " + apiKey);
            urlConnection.setRequestProperty("Content-Type", "application/json");

            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
            } else {
                Log.e(TAG, "HTTP GET error code: " + responseCode);
                throw new IOException("HTTP GET error code: " + responseCode);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return response.toString();
    }

    private String processQuestionAnswer(JSONObject finalData) throws JSONException {
        if (!finalData.has("data") || !(finalData.get("data") instanceof JSONArray)) {
            return "";
        }

        JSONArray dataArray = finalData.getJSONArray("data");
        StringBuilder answerBuilder = new StringBuilder();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject item = dataArray.getJSONObject(i);
            String type = item.optString("type");
            String content = item.optString("content");

            if ("answer".equals(type)) {
                answerBuilder.append(content).append("\n");
            }
        }

        return answerBuilder.toString().trim();
    }

    private void postError(QuestionCallback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage != null ? errorMessage : "Unknown error"));
    }
}
// 调用方法
//        GetDataByThread getDataByThread = new GetDataByThread("/getWeakWords");
//        getDataByThread.fetchWeakWords(new Handler(Looper.getMainLooper()) {
//                @Override
//                public void handleMessage(@NonNull Message msg) {
//                    if (msg.what == msg_success) {
//                        String weakWords = (String) msg.obj;
//                        // weakWords的值为：{"code":"200","weakWords":[{"headWord":"care","masteryLevel":0},{"headWord":"consider","masteryLevel":0}]}
//                        try {
//                            JSONObject jsonObject = new JSONObject(weakWords);
//                            if (jsonObject.getString("code").equals("200")) {
//                                List<String> weakWordList = new ArrayList<>();
//                                StringBuilder weakWordsStr = new StringBuilder();
//                                try {
//                                    JSONArray weakWordsArray = jsonObject.getJSONArray("weakWords");
//                                    if (weakWordsArray.length() == 0) {
//                                        String[] randomWords = LexiconResourceMap.getRandomWords();
//                                        for (String word : randomWords) {
//                                            weakWordsStr.append(word).append(", ");
//                                        }
//                                        getAiResponse(weakWordsStr.toString());
//                                        return;
//                                    }
//
//                                    for (int i = 0; i < weakWordsArray.length(); i++) {
//                                        JSONObject wordObject = weakWordsArray.getJSONObject(i);
//                                        String headWord = wordObject.getString("headWord");
//                                        weakWordList.add(headWord);
//                                        Log.i("weakWords", headWord);
//                                        weakWordsStr.append(headWord).append(", ");
//                                    }
////                                    Log.i("weakWords", weakWordsStr.toString());
//                                    markdownContentView.setText("正在上传薄弱单词……");
//                                    getAiResponse(weakWordsStr.toString());
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }
//
//                            }
//                        }catch (Exception e){
//                            e.printStackTrace();
//                        }
//
//                    }else if (msg.what == msg_failed) {
//                        Log.i("weakWords", "文章生成失败");
//                    }
//                }
//            },msg_success,msg_failed,String.valueOf(userId));
