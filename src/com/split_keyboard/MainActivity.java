package com.split_keyboard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;

import com.split_keyboard.R;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends Activity {

	Random random = new Random();
			
	Button startButton;
	Button stopButton;
	TextView stateTextView;
	RadioGroup techniqueGroup;

	TextView textView;
	TextView candidateView;
	
	boolean started = false;
	boolean eyes_free = true;
	
	final int MAX_SENTENCE = 40;
	int sentenceID = 0;
	String sentence = "";
	String inputted = "";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		textView = (TextView)findViewById(R.id.text);
		candidateView = (TextView)findViewById(R.id.candidate);
		
		startButton = (Button)findViewById(R.id.startbutton);
		stopButton = (Button)findViewById(R.id.stopbutton);
		stateTextView = (TextView)findViewById(R.id.state);
		startButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v){
				if (started == true) return;
				started = true;
				stateTextView.setText("STARTED");
				sentenceID = 1;
				sentence = sentences.get(random.nextInt(sentences.size()));
				renewText();
			}
		});
		stopButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v){
				if (started == false) return;
				started = false;
				stateTextView.setText("UNSTARTED");
				plist.clear();
				wlist.clear();
				renewCandidate();
				renewText();
			}
		});
		
		techniqueGroup = (RadioGroup)findViewById(R.id.technique);
		techniqueGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				// TODO Auto-generated method stub
				if (started == true) return;
				if (checkedId == R.id.eyes_free) {
					eyes_free = true;
				} else if (checkedId == R.id.eyes_focus) {
					eyes_free = false;
				} else {
					Log.d("error", "radio group");
				}
			}
		});
		
		load();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	
	final int MAX_WORD_LENGTH = 99;
	final int DICT_SIZE = 10000;
	ArrayList<String> sentences = new ArrayList<String>();
	ArrayList<Word>[] dict = new ArrayList[MAX_WORD_LENGTH];
	ArrayList<Word> dict_oov = new ArrayList<Word>();
	BivariateGaussian[][] model = new BivariateGaussian[2][26];
	
	void load() {
		BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.sentences)));
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				sentences.add(line.toLowerCase());
			}
			reader.close();
			Log.d("load", "finish read sentences");
		} catch (Exception e) {
			Log.d("error", "read sentences");
		}
		
		for (int i = 0; i < MAX_WORD_LENGTH; i++) {
			dict[i] = new ArrayList<Word>();
		}
		reader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.dict)));
		try {
			int lineNo = 0;
			while ((line = reader.readLine()) != null) {
				lineNo++;
				String[] arr = line.split(" ");
				String str = arr[0];
				double freq = Double.parseDouble(arr[1]);
				if (str.length() >= MAX_WORD_LENGTH) continue;
				if (lineNo <= DICT_SIZE) {
					dict[str.length()].add(new Word(str, freq));
				} else {
					dict_oov.add(new Word(str, freq));
				}
			}
			reader.close();
			Log.d("load", "finish read dictionary");
		} catch (Exception e) {
			Log.d("error", "read dictionary");
		}
		
		reader = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.model)));
		try {
			int lineNo = 0;
			while ((line = reader.readLine()) != null) {
				lineNo++;
				if (lineNo <= 1) continue;
				String[] arr = line.split(",");
				if (arr[0].equals("1")) {
					int hand = Integer.parseInt(arr[1]);
					int index = Integer.parseInt(arr[2]);
					model[hand][index] = new BivariateGaussian(Double.parseDouble(arr[3]), Double.parseDouble(arr[4]), Double.parseDouble(arr[5]), Double.parseDouble(arr[6]));
				}
			}
			reader.close();
			Log.d("load", "finish read model");
		} catch (Exception e) {
			Log.d("error", "read model");
		}
	}
	
	
	
	final int CANDIDATE_SIZE = 5;
	ArrayList<String> wlist = new ArrayList<String>();
	ArrayList<Point> plist = new ArrayList<Point>();
	ArrayList<Word> candidates = new ArrayList<Word>();
	int selected = 0;
	
	void renewText() {
		String text = "sentence: " + sentenceID + "/" + MAX_SENTENCE + "<br/><br/>";
		text += sentence + "<br/><br/>";
		for (int i = 0; i < wlist.size(); i++) text += wlist.get(i) + " ";
		if (plist.size() > 0) text += candidates.get(selected).str;
		text += "<font color='#aaaaaa'>|</font>";
		textView.setText(Html.fromHtml(text));
	}
	
	void renewCandidate() {
		candidates.clear();
		int n = plist.size();
		for (int i = 0; i < dict[n].size(); i++) {
			Word word = dict[n].get(i);
			String str = word.str;
			double p = word.value;
			for (int j = 0; j < n; j++) {
				Point q = plist.get(j);
				p *= model[q.hand][(int)(str.charAt(j) - 'a')].probability(q.x, q.y);
			}
			candidates.add(new Word(str, p));
			int j = candidates.size() - 1;
			while (j > 0 && candidates.get(j).value > candidates.get(j - 1).value) {
				Word w0 = candidates.get(j);
				Word w1 = candidates.get(j - 1);
				String tmpstr = w0.str;
				w0.str = w1.str;
				w1.str = tmpstr;
				double tmpvalue = w0.value;
				w0.value = w1.value;
				w1.value = tmpvalue;
				j--;
			}
			if (candidates.size() > CANDIDATE_SIZE) {
				candidates.remove(candidates.size() - 1);
			}
		}
		String text = "";
		for (int i = 0; i < candidates.size(); i++) {
			if (i == selected) text += "<font color='#ff0000'>";
			text += candidates.get(i).str + "  ";
			if (i == selected) text += "</font>";
		}
		candidateView.setText(Html.fromHtml(text));
		int cnt = wlist.size();
		for (int i = 0; i < wlist.size(); i++) cnt += wlist.get(i).length();
		candidateView.setX(650 + cnt * 30);
	}

	void confirmSelection(int selected) {
		if (plist.size() > 0) {
			wlist.add(candidates.get(selected).str);
			plist.clear();
			renewCandidate();
			renewText();
		}
	}
	
	void click(int x, int y) {
		if (eyes_free) {
			plist.add(new Point(x, y));
			renewCandidate();
			renewText();
		} else {
			
		}
	}
	
	void swipeLeft() {
		if (eyes_free) {
			if (plist.size() > 0) {
				plist.remove(plist.size() - 1);
				renewCandidate();
			} else if (wlist.size() > 0) {
				wlist.remove(wlist.size() - 1);
			}
			renewText();
		} else {
			
		}
	}
	
	void swipeRight() {
		if (eyes_free) {
			if (plist.size() == 0) {
				int totalLength = wlist.size() - 1;
				for (int i = 0; i < wlist.size(); i++) totalLength += wlist.get(i).length();
				Log.d("x", totalLength + " " + sentence.length());
				if (totalLength == sentence.length()) {
					sentenceID++;
					sentence = sentences.get(random.nextInt(sentences.size()));
					if (sentenceID > MAX_SENTENCE) {
						stopButton.performClick();
						return;
					}
					wlist.clear();
					renewText();
				}
			}
			confirmSelection(0);
		}
	}

	void swipeDown() {
		plist.clear();
		renewCandidate();
		renewText();
	}
	
	void drag(int x, int y, int downX, int downY) {
		double span = (2550 - downX) / 5.0;
		span = Math.min(span, 50);
		double q = (x - 20 - downX) / span;
		q = Math.max(q, 0);
		q = Math.min(q, 4);
		selected = (int)Math.round(q);
		renewCandidate();
	}
	
	void dragFinish() {
		confirmSelection(selected);
		selected = 0;
	}
	

	TouchEvent[] touchEvent = new TouchEvent[10];
	
	public boolean onTouchEvent(MotionEvent event){
		if (started == false) return super.onTouchEvent(event);
		int n = event.getPointerCount();
		int index = event.getActionIndex();
		int pointerID = event.getPointerId(index);
		int x = (int)event.getX(index);
		int y = (int)event.getY(index);
		
		switch (event.getActionMasked()){
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_POINTER_DOWN:
			touchEvent[pointerID] = new TouchEvent(x, y);
			break;
			
		case MotionEvent.ACTION_MOVE:
			for (int i = 0; i < n; i++) {
				int j = event.getPointerId(i);
				int xx = (int)event.getX(i);
				int yy = (int)event.getY(i);
				/*if (touchEvent[j].anyMove(xx, yy)) {
					drag(xx, yy, touchEvent[j].downX, touchEvent[j].downY);
				}*/
				drag(xx, yy, touchEvent[j].downX, touchEvent[j].downY);
			}
			break;
			
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
			TouchEvent q = touchEvent[pointerID];
			int op = q.up(x, y);
			switch (op) {
			case TouchEvent.EVENT_CLICK:
				click((q.x + q.downX) / 2, (q.y + q.downY) / 2);
				break;
			case TouchEvent.EVENT_SWIPE_LEFT:
				if (n == 1) swipeLeft();
				break;
			case TouchEvent.EVENT_SWIPE_RIGHT:
				if (n == 1) swipeRight();
				break;
			case TouchEvent.EVENT_SWIPE_DOWN:
				if (n == 1) swipeDown();
				break;
			case TouchEvent.EVENT_DRAG:
				if (n == 1) dragFinish();
				break;
			}
			touchEvent[pointerID] = null;
			break;
		}
		return super.onTouchEvent(event);
	}
}