package wscconnect.android.callbacks;

import org.json.JSONObject;

/**
 * @author Christopher Walz
 * @copyright 2017-2018 Christopher Walz
 * @license GNU General Public License v3.0 <https://opensource.org/licenses/LGPL-3.0>
 */

public interface SimpleJSONCallback {
    void onReady(JSONObject json, boolean success);
}
