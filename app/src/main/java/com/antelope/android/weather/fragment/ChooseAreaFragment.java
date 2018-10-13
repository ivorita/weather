package com.antelope.android.weather.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.antelope.android.weather.MainActivity;
import com.antelope.android.weather.R;
import com.antelope.android.weather.WeatherActivity;
import com.antelope.android.weather.db.City;
import com.antelope.android.weather.db.County;
import com.antelope.android.weather.db.Province;
import com.antelope.android.weather.util.HttpUtil;
import com.antelope.android.weather.util.Utility;

import org.litepal.LitePal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {

    private ProgressBar mProgressBar;
    private TextView titleText;
    private Button backBtn;
    private ListView mListView;
    private ArrayAdapter<String> mAdapter;
    private List<String> dataList = new ArrayList<>();

    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private List<Province> mProvinceList;
    private List<City> mCityList;
    private List<County> mCountyList;

    private Province selectedProvince;
    private City selectedCity;

    private int currentLevel;

    /**
     * 作用：创建Fragment中显示的View
     * @param inflater 用来装载布局文件
     * @param container 表示<fragment>标签的父标签对应的ViewGroup对象
     * @param savedInstanceState 可以获取fragment保存的状态
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText = view.findViewById(R.id.title_text);
        backBtn = view.findViewById(R.id.back_btn);
        mListView = view.findViewById(R.id.list_view);
        mAdapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        mListView.setAdapter(mAdapter);
        return view;
    }

    /**
     * 在Activity.OnCreate方法调用后会立刻调用方法onActivityCreated，
     * 表示窗口已经初始化完毕，此时可以调用控件了
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE){
                    selectedProvince = mProvinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY){
                    selectedCity = mCityList.get(position);
                    queryCounties();
                } else if (currentLevel == LEVEL_COUNTY){
                    String weatherId = mCountyList.get(position).getWeatherId();
                    if (getActivity() instanceof MainActivity){
                        Intent intent = new Intent(getActivity(),WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    } else if (getActivity() instanceof WeatherActivity){
                        WeatherActivity weatherActivity = (WeatherActivity) getActivity();
                        weatherActivity.mDrawerLayout.closeDrawers();
                        weatherActivity.mSwipeRefresh.setRefreshing(true);
                        weatherActivity.requestWeather(weatherId);
                    }

                }
            }
        });

        queryProvinces();

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentLevel == LEVEL_COUNTY){
                    queryCities();
                } else if (currentLevel == LEVEL_CITY){
                    queryProvinces();
                }
            }
        });

    }

    /**
     * 查询全国所有的省，优先从数据库查询，如果没有查询到再到服务器上查询
     */
    private void queryProvinces() {
        titleText.setText("中国");
        backBtn.setVisibility(View.GONE);
        mProvinceList = LitePal.findAll(Province.class);
        if (mProvinceList.size() > 0){
            dataList.clear();
            for (Province province : mProvinceList){
                dataList.add(province.getProvinceName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    /**
     * 查询全国所有的城市，优先从数据库查询，如果没有查询到再到服务器上查询
     */
    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backBtn.setVisibility(View.VISIBLE);
        mCityList = LitePal.where("provinceid = ?",String.valueOf(selectedProvince.getId())).find(City.class);
        if (mCityList.size() > 0){
            dataList.clear();
            for (City city : mCityList){
                dataList.add(city.getCityName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address,"city");
        }
    }

    /**
     * 查询全国所有的县，优先从数据库查询，如果没有查询到再到服务器上查询
     */
    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backBtn.setVisibility(View.VISIBLE);
        mCountyList = LitePal.where("cityid = ?",String.valueOf(selectedCity.getId())).find(County.class);
        if (mCountyList.size() > 0){
            dataList.clear();
            for (County county : mCountyList){
                dataList.add(county.getCountyName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address,"county");
        }
    }

    /**
     * @param address 传入的地址
     * @param type 传入的类型
     * 从服务器上查询省市县数据
     */
    private void queryFromServer(String address, final String type) {
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull final IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_LONG).show();
                        Log.d("异常", "run: " + e.toString());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseText = response.body().string();
                Log.d("response", responseText);
                boolean result = false;
                if ("province".equals(type)){
                    result = Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                } else if ("county".equals(type)){
                    result = Utility.handleCountyResponse(responseText,selectedCity.getId());
                }
                if (result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ("province".equals(type)){
                                queryProvinces();
                            } else if ("city".equals(type)){
                                queryCities();
                            } else if ("county".equals(type)){
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

}
