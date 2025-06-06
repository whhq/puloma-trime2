/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.buweima.trime;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.util.DisplayMetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;


/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         android:keyWidth="%10p"
 *         android:keyHeight="50px"
 *         android:horizontalGap="2px"
 *         android:verticalGap="2px" &gt;
 *     &lt;Row android:keyWidth="32px" &gt;
 *         &lt;Key android:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 * @attr ref android.R.styleable#Keyboard_keyWidth
 * @attr ref android.R.styleable#Keyboard_keyHeight
 * @attr ref android.R.styleable#Keyboard_horizontalGap
 * @attr ref android.R.styleable#Keyboard_verticalGap
 */
public class Keyboard {

    static final String TAG = "Keyboard";

    // Keyboard XML Tags
    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";

    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    public static final int KEYCODE_SHIFT = -1;
    //public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT = -6;
    public static final int KEYCODE_CLEAR = -7;

    public static final int KEYCODE_OPTIONS = -10;
    public static final int KEYCODE_SCHEMA_OPTIONS = -11;
    public static final int KEYCODE_REVERSE = -12;

    public static final int KEYCODE_MODE_LAST = -20;
    public static final int KEYCODE_MODE_PREV = -21;
    public static final int KEYCODE_MODE_NEXT = -22;
    public static final int KEYCODE_MODE_SWITCH = -30;
    
    /** Keyboard label **/
    //private CharSequence mLabel;

    /** Horizontal gap default for all rows */
    private int mDefaultHorizontalGap;
    
    /** Default key width */
    private int mDefaultWidth;

    /** Default key height */
    private int mDefaultHeight;

    /** Default gap between rows */
    private int mDefaultVerticalGap;

    /** Is the keyboard in the shifted state */
    private boolean mShifted;
    
    /** Key instance for the shift key, if present */
    private Key mShiftKey;
    
    /** Key index for the shift key, if present */
    private int mShiftKeyIndex = -1;
    
    /** Current key width, while loading the keyboard */
    //private int mKeyWidth;
    
    /** Current key height, while loading the keyboard */
    //private int mKeyHeight;
    
    /** Total height of the keyboard, including the padding and keys */
    private int mTotalHeight;
    
    /** 
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
     * right side.
     */
    private int mTotalWidth;
    
    /** List of keys in this keyboard */
    private List<Key> mKeys;
    
    /** List of modifier keys such as Shift & Alt, if any */
    private List<Key> mModifierKeys;
    
    /** Width of the screen available to fit the keyboard */
    private int mDisplayWidth;

    /** Height of the screen */
    private int mDisplayHeight;

    /** Keyboard mode, or zero, if none.  */
    private int mKeyboardMode;
    private int mAsciiMode;

    // Variables for pre-computing nearest keys.
    
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.4f;

    /**
     * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate. 
     * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
     * defines. 
     * @attr ref android.R.styleable#Keyboard_keyWidth
     * @attr ref android.R.styleable#Keyboard_keyHeight
     * @attr ref android.R.styleable#Keyboard_horizontalGap
     * @attr ref android.R.styleable#Keyboard_verticalGap
     * @attr ref android.R.styleable#Keyboard_Row_rowEdgeFlags
     * @attr ref android.R.styleable#Keyboard_Row_keyboardMode
     */
    public static class Row {
        /** Default width of a key in this row. */
        public int defaultWidth;
        /** Default height of a key in this row. */
        public int defaultHeight;
        /** Default horizontal gap between keys in this row. */
        public int defaultHorizontalGap;
        /** Vertical gap following this row. */
        public int verticalGap;
        /**
         * Edge flags for this row of keys. Possible values that can be assigned are
         * {@link Keyboard#EDGE_TOP EDGE_TOP} and {@link Keyboard#EDGE_BOTTOM EDGE_BOTTOM}  
         */
        public int rowEdgeFlags;
        
        /** The keyboard mode for this row */
        public int mode;
        
        private Keyboard parent;

        public Row(Keyboard parent) {
            this.parent = parent;
        }
        
        public Row(Resources res, Keyboard parent, XmlResourceParser parser) {
            this.parent = parent;
            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), 
                    R.styleable.Keyboard);
            defaultWidth = getDimensionOrFraction(a, 
                    R.styleable.Keyboard_keyWidth,
                    parent.mDisplayWidth, parent.mDefaultWidth);
            defaultHeight = getDimensionOrFraction(a, 
                    R.styleable.Keyboard_keyHeight,
                    parent.mDisplayHeight, parent.mDefaultHeight);
            defaultHorizontalGap = getDimensionOrFraction(a,
                    R.styleable.Keyboard_horizontalGap,
                    parent.mDisplayWidth, parent.mDefaultHorizontalGap);
            verticalGap = getDimensionOrFraction(a, 
                    R.styleable.Keyboard_verticalGap,
                    parent.mDisplayHeight, parent.mDefaultVerticalGap);
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard_Row);
            rowEdgeFlags = a.getInt(R.styleable.Keyboard_Row_rowEdgeFlags, 0);
            mode = a.getResourceId(R.styleable.Keyboard_Row_keyboardMode,
                    0);
            a.recycle();
        }
    }

    /**
     * Class for describing the position and characteristics of a single key in the keyboard.
     * 
     * @attr ref android.R.styleable#Keyboard_keyWidth
     * @attr ref android.R.styleable#Keyboard_keyHeight
     * @attr ref android.R.styleable#Keyboard_horizontalGap
     * @attr ref android.R.styleable#Keyboard_Key_codes
     * @attr ref android.R.styleable#Keyboard_Key_keyIcon
     * @attr ref android.R.styleable#Keyboard_Key_keyLabel
     * @attr ref android.R.styleable#Keyboard_Key_iconPreview
     * @attr ref android.R.styleable#Keyboard_Key_isSticky
     * @attr ref android.R.styleable#Keyboard_Key_isRepeatable
     * @attr ref android.R.styleable#Keyboard_Key_isModifier
     * @attr ref android.R.styleable#Keyboard_Key_popupKeyboard
     * @attr ref android.R.styleable#Keyboard_Key_popupCharacters
     * @attr ref android.R.styleable#Keyboard_Key_keyOutputText
     * @attr ref android.R.styleable#Keyboard_Key_keyEdgeFlags
     */
    public static class Key {
        /** 
         * All the key codes (unicode or custom code) that this key could generate, zero'th 
         * being the most important.
         */
        public int[] codes;
        
        /** Label to display */
        public CharSequence label;
        
        /** Icon to display instead of a label. Icon takes precedence over a label */
        public Drawable icon;
        /** Preview version of the icon, for the preview popup */
        public Drawable iconPreview;
        /** Width of the key, not including the gap */
        public int width;
        /** Height of the key, not including the gap */
        public int height;
        /** The horizontal gap before this key */
        public int gap;
        /** Whether this key is sticky, i.e., a toggle key */
        public boolean sticky;
        /** X coordinate of the key in the keyboard layout */
        public int x;
        /** Y coordinate of the key in the keyboard layout */
        public int y;
        /** The current pressed state of this key */
        public boolean pressed;
        /** If this is a sticky key, is it on? */
        public boolean on;
        /** Text to output when pressed. This can be multiple characters, like ".com" */
        public CharSequence text;
        /** Popup characters */
        public CharSequence popupCharacters;
        
        public String symbol, symbolLabel, hint;
        public int symbolCode;

        public CharSequence labelPreview;

        /** 
         * Flags that specify the anchoring to edges of the keyboard for detecting touch events
         * that are just out of the boundary of the key. This is a bit mask of 
         * {@link Keyboard#EDGE_LEFT}, {@link Keyboard#EDGE_RIGHT}, {@link Keyboard#EDGE_TOP} and
         * {@link Keyboard#EDGE_BOTTOM}.
         */
        public int edgeFlags;
        /** Whether this is a modifier key, such as Shift or Alt */
        public boolean modifier;
        /** The keyboard that this key belongs to */
        private Keyboard keyboard;
        /** 
         * If this key pops up a mini keyboard, this is the resource id for the XML layout for that
         * keyboard.
         */
        public int popupResId;
        /** Whether this key repeats itself when held down */
        public boolean repeatable;

        
        private final static int[] KEY_STATE_NORMAL_ON = { 
            android.R.attr.state_checkable, 
            android.R.attr.state_checked
        };
        
        private final static int[] KEY_STATE_PRESSED_ON = { 
            android.R.attr.state_pressed, 
            android.R.attr.state_checkable, 
            android.R.attr.state_checked 
        };
        
        private final static int[] KEY_STATE_NORMAL_OFF = { 
            android.R.attr.state_checkable 
        };
        
        private final static int[] KEY_STATE_PRESSED_OFF = { 
            android.R.attr.state_pressed, 
            android.R.attr.state_checkable 
        };
        
        private final static int[] KEY_STATE_NORMAL = {
        };
        
        private final static int[] KEY_STATE_PRESSED = {
            android.R.attr.state_pressed
        };

        /** Create an empty key with no attributes. */
        public Key(Row parent) {
            keyboard = parent.parent;
        }
        
        /** Create a key with the given top-left coordinate and extract its attributes from
         * the XML parser.
         * @param res resources associated with the caller's context
         * @param parent the row that this key belongs to. The row must already be attached to
         * a {@link Keyboard}.
         * @param x the x coordinate of the top-left
         * @param y the y coordinate of the top-left
         * @param parser the XML parser containing the attributes for this key
         */
        public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
            this(parent);

            this.x = x;
            this.y = y;
            
            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), 
                    R.styleable.Keyboard);

            width = getDimensionOrFraction(a, 
                    R.styleable.Keyboard_keyWidth,
                    keyboard.mDisplayWidth, parent.defaultWidth);
            height = getDimensionOrFraction(a, 
                    R.styleable.Keyboard_keyHeight,
                    keyboard.mDisplayHeight, parent.defaultHeight);
            gap = getDimensionOrFraction(a, 
                    R.styleable.Keyboard_horizontalGap,
                    keyboard.mDisplayWidth, parent.defaultHorizontalGap);
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard_Key);
            this.x += gap;
            TypedValue codesValue = new TypedValue();
            a.getValue(R.styleable.Keyboard_Key_codes,
                    codesValue);
            if (codesValue.type == TypedValue.TYPE_INT_DEC 
                    || codesValue.type == TypedValue.TYPE_INT_HEX) {
                codes = new int[] { codesValue.data };
            } else if (codesValue.type == TypedValue.TYPE_STRING) {
                codes = parseCSV(codesValue.string.toString());
            }
            
            iconPreview = a.getDrawable(R.styleable.Keyboard_Key_iconPreview);
            if (iconPreview != null) {
                iconPreview.setBounds(0, 0, iconPreview.getIntrinsicWidth(), 
                        iconPreview.getIntrinsicHeight());
            }
            popupCharacters = a.getText(
                    R.styleable.Keyboard_Key_popupCharacters);
            popupResId = a.getResourceId(
                    R.styleable.Keyboard_Key_popupKeyboard, 0);
            repeatable = a.getBoolean(
                    R.styleable.Keyboard_Key_isRepeatable, false);
            modifier = a.getBoolean(
                    R.styleable.Keyboard_Key_isModifier, false);
            sticky = a.getBoolean(
                    R.styleable.Keyboard_Key_isSticky, false);
            edgeFlags = a.getInt(R.styleable.Keyboard_Key_keyEdgeFlags, 0);
            edgeFlags |= parent.rowEdgeFlags;

            icon = a.getDrawable(
                    R.styleable.Keyboard_Key_keyIcon);
            if (icon != null) {
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            }
            label = a.getText(R.styleable.Keyboard_Key_keyLabel);
            text = a.getText(R.styleable.Keyboard_Key_keyOutputText);
            
            if (codes == null && !TextUtils.isEmpty(label)) {
                codes = new int[] { label.charAt(0) };
            }
            a.recycle();
        }
        
        /**
         * Informs the key that it has been pressed, in case it needs to change its appearance or
         * state.
         * @see #onReleased(boolean)
         */
        public void onPressed() {
            pressed = !pressed;
        }
        
        /**
         * Changes the pressed state of the key. If it is a sticky key, it will also change the
         * toggled state of the key if the finger was release inside.
         * @param inside whether the finger was released inside the key
         * @see #onPressed()
         */
        public void onReleased(boolean inside) {
            pressed = !pressed;
            if (sticky) {
                on = !on;
            }
        }

        int[] parseCSV(String value) {
            int count = 0;
            int lastIndex = 0;
            if (value.length() > 0) {
                count++;
                while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
                    count++;
                }
            }
            int[] values = new int[count];
            count = 0;
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = Integer.parseInt(st.nextToken());
                } catch (NumberFormatException nfe) {
                    Log.e(TAG, "Error parsing keycodes " + value);
                }
            }
            return values;
        }

        /**
         * Detects if a point falls inside this key.
         * @param x the x-coordinate of the point 
         * @param y the y-coordinate of the point
         * @return whether or not the point falls inside the key. If the key is attached to an edge,
         * it will assume that all points between the key and the edge are considered to be inside
         * the key.
         */
        public boolean isInside(int x, int y) {
            boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
            boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
            boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
            boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
            if ((x >= this.x || (leftEdge && x <= this.x + this.width)) 
                    && (x < this.x + this.width || (rightEdge && x >= this.x)) 
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y))) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns the square of the distance between the center of the key and the given point.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return the square of the distance of the point from the center of the key
         */
        public int squaredDistanceFrom(int x, int y) {
            int xDist = this.x + width / 2 - x;
            int yDist = this.y + height / 2 - y;
            return xDist * xDist + yDist * yDist;
        }
        
        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable#setState(int[])
         */
        public int[] getCurrentDrawableState() {
            int[] states = KEY_STATE_NORMAL;

            if (on) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_ON;
                } else {
                    states = KEY_STATE_NORMAL_ON;
                }
            } else {
                if (sticky) {
                    if (pressed) {
                        states = KEY_STATE_PRESSED_OFF;
                    } else {
                        states = KEY_STATE_NORMAL_OFF;
                    }
                } else {
                    if (pressed) {
                        states = KEY_STATE_PRESSED;
                    }
                }
            }
            return states;
        }
    }

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     */
    public Keyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, 0);
    }
    
    /**
     * Creates a keyboard from the given xml key layout file. Weeds out rows
     * that have a keyboard mode defined but don't match the specified mode. 
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param modeId keyboard mode identifier
     */
    public Keyboard(Context context, int xmlLayoutResId, int modeId) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDisplayWidth = dm.widthPixels;
        mDisplayHeight = dm.heightPixels;
        //Log.v(TAG, "keyboard's display metrics:" + dm);

        mDefaultHorizontalGap = 0;
        mDefaultWidth = mDisplayWidth / 10;
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mKeys = new ArrayList<Key>();
        mModifierKeys = new ArrayList<Key>();
        mKeyboardMode = modeId;
        loadKeyboard(context, context.getResources().getXml(xmlLayoutResId));
    }

    /**
     * <p>Creates a blank keyboard from the given resource file and populates it with the specified
     * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
     * </p>
     * <p>If the specified number of columns is -1, then the keyboard will fit as many keys as
     * possible in each row.</p>
     * @param context the application or service context
     * @param layoutTemplateResId the layout template file, containing no keys.
     * @param characters the list of characters to display on the keyboard. One key will be created
     * for each character.
     * @param columns the number of columns of keys to display. If this number is greater than the 
     * number of keys that can fit in a row, it will be ignored. If this number is -1, the 
     * keyboard will fit as many keys as possible in each row.
     */
    public Keyboard(Context context, int layoutTemplateResId, 
            CharSequence characters, int columns, int horizontalPadding) {
        this(context, layoutTemplateResId);
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;
        
        Row row = new Row(this);
        row.defaultHeight = mDefaultHeight;
        row.defaultWidth = mDefaultWidth;
        row.defaultHorizontalGap = mDefaultHorizontalGap;
        row.verticalGap = mDefaultVerticalGap;
        row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;
        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (column >= maxColumns 
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
            }
            final Key key = new Key(row);
            key.x = x;
            key.y = y;
            key.width = mDefaultWidth;
            key.height = mDefaultHeight;
            key.gap = mDefaultHorizontalGap;
            key.label = String.valueOf(c);
            key.codes = new int[] { c };
            column++;
            x += key.width + key.gap;
            mKeys.add(key);
            if (x > mTotalWidth) {
                mTotalWidth = x;
            }
        }
        mTotalHeight = y + mDefaultHeight; 
    }
    
    public List<Key> getKeys() {
        return mKeys;
    }
    
    public List<Key> getModifierKeys() {
        return mModifierKeys;
    }
    
    protected int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }
    
    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    protected int getKeyHeight() {
        return mDefaultHeight;
    }

    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }

    protected int getKeyWidth() {
        return mDefaultWidth;
    }
    
    protected void setKeyWidth(int width) {
        mDefaultWidth = width;
    }

    /**
     * Returns the total height of the keyboard
     * @return the total height of the keyboard
     */
    public int getHeight() {
        return mTotalHeight;
    }
    
    public int getMinWidth() {
        return mTotalWidth;
    }

    public boolean setShifted(boolean shiftState) {
        if (mShiftKey != null) {
            mShiftKey.on = shiftState;
        }
        if (mShifted != shiftState) {
            mShifted = shiftState;
            return true;
        }
        return false;
    }

    public boolean isShifted() {
        return mShifted;
    }

    public int getShiftKeyIndex() {
        return mShiftKeyIndex;
    }
    
    private void computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        mGridNeighbors = new int[GRID_SIZE][];
        int[] indices = new int[mKeys.size()];
        final int gridWidth = GRID_WIDTH * mCellWidth;
        final int gridHeight = GRID_HEIGHT * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1) 
                                < mProximityThreshold ||
                            key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold) {
                        indices[count++] = i;
                    }
                }
                int [] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
            }
        }
    }
    
    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
            if (index < GRID_SIZE) {
                return mGridNeighbors[index];
            }
        }
        return new int[0];
    }

    protected Row createRowFromXml(Resources res, XmlResourceParser parser) {
        return new Row(res, this, parser);
    }
    
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, 
            XmlResourceParser parser) {
        return new Key(res, parent, x, y, parser);
    }

    private void loadKeyboard(Context context, XmlResourceParser parser) {
        boolean inKey = false;
        boolean inRow = false;
        // boolean leftMostKey = false;
        // int row = 0;
        int x = 0;
        int y = 0;
        Key key = null;
        Row currentRow = null;
        Resources res = context.getResources();
        boolean skipRow = false;
        
        try {
            int event;
            while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG) {
                    String tag = parser.getName();
                    if (TAG_ROW.equals(tag)) {
                        inRow = true;
                        x = 0;
                        currentRow = createRowFromXml(res, parser);
                        skipRow = currentRow.mode != 0 && currentRow.mode != mKeyboardMode;
                        if (skipRow) {
                            skipToEndOfRow(parser);
                            inRow = false;
                        }
                   } else if (TAG_KEY.equals(tag)) {
                        inKey = true;
                        key = createKeyFromXml(res, currentRow, x, y, parser);
                        mKeys.add(key);
                        if (key.codes[0] == KEYCODE_SHIFT) {
                            mShiftKey = key;
                            mShiftKeyIndex = mKeys.size()-1;
                            mModifierKeys.add(key);
                        } else if (key.codes[0] == KEYCODE_ALT) {
                            mModifierKeys.add(key);
                        }
                    } else if (TAG_KEYBOARD.equals(tag)) {
                        parseKeyboardAttributes(res, parser);
                    }
                } else if (event == XmlResourceParser.END_TAG) {
                    if (inKey) {
                        inKey = false;
                        x += key.gap + key.width;
                        if (x > mTotalWidth) {
                            mTotalWidth = x;
                        }
                    } else if (inRow) {
                        inRow = false;
                        y += currentRow.verticalGap;
                        y += currentRow.defaultHeight;
                        // row++;
                    } else {
                        // TODO: error or extend?
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error:" + e);
            e.printStackTrace();
        }
        mTotalHeight = y - mDefaultVerticalGap;
    }

    private void skipToEndOfRow(XmlResourceParser parser) 
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.END_TAG 
                    && parser.getName().equals(TAG_ROW)) {
                break;
            }
        }
    }
    
    private void parseKeyboardAttributes(Resources res, XmlResourceParser parser) {
        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), 
                R.styleable.Keyboard);

        mDefaultWidth = getDimensionOrFraction(a,
                R.styleable.Keyboard_keyWidth,
                mDisplayWidth, mDisplayWidth / 10);
        mDefaultHeight = getDimensionOrFraction(a,
                R.styleable.Keyboard_keyHeight,
                mDisplayHeight, 50);
        mDefaultHorizontalGap = getDimensionOrFraction(a,
                R.styleable.Keyboard_horizontalGap,
                mDisplayWidth, 0);
        mDefaultVerticalGap = getDimensionOrFraction(a,
                R.styleable.Keyboard_verticalGap,
                mDisplayHeight, 0);
        mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
        mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
        a.recycle();
    }
    
    static int getDimensionOrFraction(TypedArray a, int index, int base, int defValue) {
        TypedValue value = a.peekValue(index);
        if (value == null) return defValue;
        if (value.type == TypedValue.TYPE_DIMENSION) {
            return a.getDimensionPixelOffset(index, defValue);
        } else if (value.type == TypedValue.TYPE_FRACTION) {
            // Round it to avoid values like 47.9999 from getting truncated
            return Math.round(a.getFraction(index, base, base, defValue));
        }
        return defValue;
    }

  private Object getValue(Map<String,Object> m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  public Keyboard(Context context, Object o) {
    this(context, R.xml.template);
    Map<String,Object> m = (Map<String,Object>)o;
    int columns = (Integer)getValue(m, "columns", 10);
    int defaultWidth = (Integer)getValue(m, "width", 0) * mDisplayWidth / 100;
    if (defaultWidth == 0) defaultWidth = mDefaultWidth;
    List<Map<String,Object>> lm = (List<Map<String,Object>>)m.get("keys");
    mKeyboardMode = (Integer)getValue(m, "mode", 0);
    mAsciiMode = (Integer)getValue(m, "ascii_mode", 1);
    int defaultHGap = (Integer)getValue(m, "horizontalGap", 0) * mDisplayWidth / 100;
    if (defaultHGap == 0) defaultHGap = mDefaultHorizontalGap;

    int x = 0;
    int y = 0;
    int column = 0;
    mTotalWidth = 0;

    Row row = new Row(this);
    row.defaultHeight = mDefaultHeight;
    row.defaultWidth = defaultWidth;
    row.defaultHorizontalGap = defaultHGap;
    row.verticalGap = mDefaultVerticalGap;
    row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;

    final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
    for (Map<String,Object> mk: lm) {
      int gap = (Integer)getValue(m, "horizontalGap", 0) * mDisplayWidth / 100;
      if (gap == 0) gap = defaultHGap;
      int w = (Integer)getValue(mk, "width", 0) * mDisplayWidth / 100;
      if (w == 0 && mk.containsKey("text")) w = defaultWidth;
      if (column >= maxColumns 
              || x + w > mDisplayWidth) {
          x = 0;
          y += mDefaultVerticalGap + mDefaultHeight;
          column = 0;
      }
      if(!mk.containsKey("text")){
          x += w + gap;
          continue; //縮進
      }

      final Key key = new Key(row);
      key.x = x;
      key.y = y;
      key.width = w;
      key.height = mDefaultHeight;
      key.gap = gap;
      
      key.text = (String)getValue(mk, "text", null);
      key.label = (String)getValue(mk, "label", null);
      key.labelPreview = (String)getValue(mk, "preview", null);
      key.symbol = (String)getValue(mk, "symbol", null);
      key.symbolLabel = (String)getValue(mk, "symbolLabel", null);
      key.symbolCode = (Integer)getValue(mk, "symbolCode", 0);
      key.hint = (String)getValue(mk, "hint", null);
      
      String s = (String)key.text;
      int c = s.codePointAt(0);
      key.codes = new int[]{c};

      if (s.contentEquals("<space>")){
        key.codes = new int[] {' '};
        if (key.label==null) key.label = "␣";
        key.repeatable = true;
      } else if(s.contentEquals("<shift>")){
        key.codes = new int[] {KEYCODE_SHIFT};
        if (key.label==null) key.label = "⇪";
        key.modifier = true;
        key.sticky = true;
        mShiftKey = key;
        mShiftKeyIndex = mKeys.size()-2;
        mModifierKeys.add(key);
      } else if(s.contentEquals("<delete>")){
        key.codes = new int[] {KEYCODE_DELETE};
        if (key.label==null) key.label = "⌫";
        key.repeatable = true;
      } else if(s.contentEquals("<clear>")){
        key.codes = new int[] {KEYCODE_CLEAR};
        if (key.label==null) key.label = "⎚";
      } else if(s.contentEquals("<enter>")){
        key.codes = new int[] {'\n'};
        key.text = "\n";
        if (key.label==null) key.label = "⏎";
      } else if(s.contentEquals("<switch>")){
        key.codes = new int[] {KEYCODE_MODE_SWITCH - (Integer)getValue(mk, "switch", 0)};
        if (key.label==null)	key.label = "⌨";
        if (key.symbolCode == 0) key.symbolCode = KEYCODE_OPTIONS;
      } else if(s.contentEquals("<switch_last>")){
        key.codes = new int[] {KEYCODE_MODE_LAST};
        if (key.label==null)	key.label = "⟲";
      } else if(s.contentEquals("<switch_prev>")){
        key.codes = new int[] {KEYCODE_MODE_PREV};
        if (key.label==null)	key.label = "⬆";
      } else if(s.contentEquals("<switch_next>")){
        key.codes = new int[] {KEYCODE_MODE_NEXT};
        if (key.label==null)	key.label = "⬇";
      }
      if(key.codes[0]<=' ') key.text = null;
      else if (key.label == null) key.label = key.text;
      
      if (key.icon != null) {
          key.icon.setBounds(0, 0, key.icon.getIntrinsicWidth(), key.icon.getIntrinsicHeight());
      }
      if (key.iconPreview != null) {
          key.iconPreview.setBounds(0, 0, key.iconPreview.getIntrinsicWidth(), 
                  key.iconPreview.getIntrinsicHeight());
      }
      column++;
      x += key.width + key.gap;
      mKeys.add(key);
      if (x > mTotalWidth) {
          mTotalWidth = x;
      }
    }
    mTotalHeight = y + mDefaultHeight; 
  }

  public boolean getAsciiMode() {
    return mAsciiMode != 0;
  }
}
