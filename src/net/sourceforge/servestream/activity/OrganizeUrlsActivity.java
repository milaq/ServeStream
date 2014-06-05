/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream.activity;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.adapter.OrganizeAdapter;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.dslv.DragSortController;
import net.sourceforge.servestream.dslv.DragSortListView;
import net.sourceforge.servestream.preference.UserPreferences;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class OrganizeUrlsActivity extends ActionBarActivity {

	private static final int MENU_ID_ACCEPT = 2;
	
	private List<UriBean> mBaselineUris;
	
	private DragSortListView mList;
	private OrganizeAdapter mAdapter;
	private StreamDatabase mStreamdb;
	
	private ActionMode mActionMode;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_organize_urls);
		
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		mList = (DragSortListView) findViewById(android.R.id.list);
		mList.setEmptyView(findViewById(android.R.id.empty));
		mList.setDropListener(dropListener);

		DragSortController controller = new DragSortController(mList);
		controller.setDragInitMode(DragSortController.ON_DRAG);
		controller.setDragHandleId(R.id.drag_handle);
		mList.setOnTouchListener(controller);
		
		mAdapter = new OrganizeAdapter(this, new ArrayList<UriBean>());
		mList.setAdapter(mAdapter);
		mList.setItemsCanFocus(false);
		mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (mActionMode == null) {
					startSupportActionMode(mActionModeCallback);
				}
			}
		});
	}
	
	public void onStart() {
		super.onStart();
		
		mStreamdb = new StreamDatabase(this);
		updateList();
	}
	
	public void onStop() {
		super.onStop();
		
		mStreamdb.close();
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case android.R.id.home:
    			Intent intent = new Intent(this, MainActivity.class);
    			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
    			return true;
    		case MENU_ID_ACCEPT:
    			saveChanges();
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		TypedArray drawables = obtainStyledAttributes(new int[] { R.attr.ic_action_accept });
		MenuItem item = menu.add(Menu.NONE, MENU_ID_ACCEPT, Menu.NONE, R.string.confirm_label);
		item.setIcon(drawables.getDrawable(0));
	    MenuItemCompat.setShowAsAction(item,
	    		MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
				| MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
		return true;
	}
	
	private void updateList() {
		mAdapter.clear();
		mBaselineUris = mStreamdb.getUris();
		for (int i = 0; i < mBaselineUris.size(); i++) {
			mAdapter.add(mBaselineUris.get(i));
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private synchronized void saveChanges() {
		List<UriBean> uris = new ArrayList<UriBean>();
		
		for (int i = 0; i < mAdapter.getCount(); i++) {
			uris.add(mAdapter.getItem(i));
		}
		
		List<UriBean> urisToDelete = new ArrayList<UriBean>();
		
		for (int i = 0; i < mBaselineUris.size(); i++) {
			if (!uris.contains(mBaselineUris.get(i))) {
				mStreamdb.deleteUri(mBaselineUris.get(i));
				urisToDelete.add(mBaselineUris.get(i));
			}
		}
		
		for (int i = 0; i < urisToDelete.size(); i++) {
			mBaselineUris.remove(urisToDelete.get(i));
		}
		
		int listPosition = 1;
		
		ContentValues values = new ContentValues();
		
		for (int i = 0; i < uris.size(); i++) {
			values.clear();
			values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, listPosition);
			listPosition++;
			
			mStreamdb.updateUri(uris.get(i), values);
		}
		
		updateList();
	}
	
	private DragSortListView.DropListener dropListener = new DragSortListView.DropListener() {

		@Override
		public void drop(int from, int to) {
			UriBean item = mAdapter.getItem(from);
			
            mAdapter.remove(item);
            mAdapter.insert(item, to);
		}
	};

	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.menu_item_delete:
				SparseBooleanArray checked = mList.getCheckedItemPositions();
				List<UriBean> selectedItems = new ArrayList<UriBean>();
		        for (int i = 0; i < checked.size(); i++) {
		            // Item position in adapter
		            int position = checked.keyAt(i);
		            // Add sport if it is checked i.e.) == TRUE!
		            if (checked.valueAt(i)) {
		                selectedItems.add(mAdapter.getItem(position));
		            }
		        }
		        
		        for (int i = 0; i < selectedItems.size(); i++) {
		        	mStreamdb.deleteUri(selectedItems.get(i));
		        }
		        
				mode.finish(); // Action picked, so close the CAB
				
				updateList();
				return true;
			default:
				return false;
			}
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.organize_urls_menu, menu);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			mActionMode = mode;
			return false;
		}
	};
}
