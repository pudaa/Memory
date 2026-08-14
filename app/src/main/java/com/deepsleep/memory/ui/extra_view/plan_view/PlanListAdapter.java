package com.deepsleep.memory.ui.extra_view.plan_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.deepsleep.memory.R;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap.getLexiconName;
import static com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap.loadBooksFromJson;

public class PlanListAdapter extends BaseAdapter {
    private final List<JSONObject> filteredPlans;
    private final Context context;
    private final int onPlanId;
    private int planId;

    public PlanListAdapter(Context context, List<JSONObject> filteredBooks, int onPlanId) {
        this.context = context;
        this.filteredPlans = filteredBooks;
        this.onPlanId = onPlanId;
    }

    @Override
    public int getCount() {
        return filteredPlans.size();
    }

    @Override
    public Object getItem(int i) {
        return filteredPlans.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @SuppressLint("ViewHolder")
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = LayoutInflater.from(this.context).inflate(R.layout.item_plan_list, viewGroup, false);
        List<JSONObject> allBooks = loadBooksFromJson(this.context);
        TextView lexiconText = view.findViewById(R.id.lexicon_id);
        TextView planProgressText = view.findViewById(R.id.plan_progress_text);
        ProgressBar planProgressBar = view.findViewById(R.id.plan_progress);
        CardView planItem = view.findViewById(R.id.plan_item);

        JSONObject plan = filteredPlans.get(i);
        String lexiconId = plan.optString("lexiconId");
        int learnedWords = plan.optInt("learnedWords");
        int totalWords = plan.optInt("totalWords");
        planId = plan.optInt("planId");

        lexiconText.setText(getLexiconName(lexiconId, allBooks));
        planProgressText.setText(String.format(Locale.getDefault(), "%d/%d", learnedWords, totalWords));
        if (totalWords > 0) {
            int progress = (learnedWords * 100) / totalWords;
            planProgressBar.setProgress(progress);
        } else {
            planProgressBar.setProgress(0);
        }
        Logger.getLogger("PlanListAdapter").info("planId: " + planId + " onePlanId: " + onPlanId);
        if (planId == onPlanId) {
            planItem.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background_stress));
        } else {
            planItem.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background));
        }
        return view;
    }

}
