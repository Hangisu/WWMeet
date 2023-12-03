package com.example.wwmeet_android;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wwmeet_android.appointment.info.AppointmentInfoAfterActivity;
import com.example.wwmeet_android.domain.MyAppointment;
import com.example.wwmeet_android.dto.FindAppointmentListResponse;
import com.example.wwmeet_android.network.RetrofitProvider;
import com.example.wwmeet_android.network.RetrofitService;
import com.example.wwmeet_android.network.SseEventService;
import com.example.wwmeet_android.util.SharedPreferenceUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    LinearLayout appointmentListBox;
    Button enterBtn;
    Button createApmBtn;
    List<FindAppointmentListResponse> appointmentList = new ArrayList<>();
    private RetrofitService retrofitService;
    SharedPreferenceUtil sharedPreferenceUtil;
    ImageView mainLogo;
    ImageView smallLogo;
    com.example.wwmeet_android.AppointmentListAdapter listAdapter = new com.example.wwmeet_android.AppointmentListAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        listAdapter.setItemClickListener(new com.example.wwmeet_android.AppointmentListAdapter.OnItemClickEventListener() {
            @Override
            public void onItemClick(View view, int position) {
                appointmentList.get(position).setFinishVote(true); // fake
                if(appointmentList.get(position).isFinishVote()){
                    // 끝난 후 약속 요청
                    Intent intent = new Intent(getApplicationContext(), AppointmentInfoAfterActivity.class);

                    startActivity(intent);
                }else{
                    Intent intent = null;

                    Call<Boolean> voteStatusCall = retrofitService.getVoteStatusOfParticipant(
                            appointmentList.get(position).getId(), appointmentList.get(position).getName());
                    voteStatusCall.enqueue(new Callback<Boolean>() {
                        @Override
                        public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                            if(!response.isSuccessful()){
                                Toast.makeText(MainActivity.this, "투표 상태 조회에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                try {
                                    Log.e("투표 상태 조회에 실패했습니다.", response.errorBody().string());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                return;
                            }
                            Intent intent = null;
                            if (response.body()) {
                                intent = new Intent(getApplicationContext(), com.example.wwmeet_android.AppointmentInfoBeforeActivity.class);
                                intent.putExtra("appointmentId", appointmentList.get(position).getId());
                            }else{
                                intent = new Intent(getApplicationContext(), com.example.wwmeet_android.VoteScheduleActivity.class);
                                intent.putExtra("appointmentId", appointmentList.get(position).getId());
                                intent.putExtra("participantName", appointmentList.get(position).getName());
                            }
                            startActivity(intent);
                        }

                        @Override
                        public void onFailure(Call<Boolean> call, Throwable t) {
                            Toast.makeText(MainActivity.this, "서버 연결에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            Log.e("서버 연결에 실패했습니다.", t.getMessage());
                        }
                    });


                }
            }
        });
        createApmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 약속 만들기
                Intent intent = new Intent(getApplicationContext(), com.example.wwmeet_android.AppointmentCreateActivity.class);
                startActivity(intent);
            }
        });

        enterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 입장하기
                Intent intent = new Intent(getApplicationContext(), com.example.wwmeet_android.EntranceAppointmentActivity.class);
                startActivity(intent);
            }
        });

        setSSE("test");

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("resume", "resume");
        appointmentList.clear();
        getAppointmentList();
    }

    private void init(){
        recyclerView = findViewById(R.id.home_recyclerview);
        appointmentListBox = findViewById(R.id.main_apm_list_box);
        enterBtn = findViewById(R.id.home_enter_appoint_btn);
        createApmBtn = findViewById(R.id.home_create_appoint_btn);
        sharedPreferenceUtil = new SharedPreferenceUtil(getApplicationContext());
        mainLogo = findViewById(R.id.main_logo_img);
        smallLogo = findViewById(R.id.main_logo_small_img);

        RetrofitProvider retrofitProvider = new RetrofitProvider();
        retrofitService = retrofitProvider.getService();

        sharedPreferenceUtil = new SharedPreferenceUtil(getApplicationContext());
    }

    private void getAppointmentList(){
        SqliteHelper database = new SqliteHelper(getApplicationContext(), "appointmentDB", null, 1);

        List<MyAppointment> myAppointmentList = database.findAllMyAppointment();

        List<Long> idList = new ArrayList<>();
        myAppointmentList.forEach((a) -> idList.add(a.getAppointmentId()));

        Call<List<FindAppointmentListResponse>> findAppointmentCall = retrofitService.findAppointmentList(idList);
        findAppointmentCall.enqueue(new Callback<List<FindAppointmentListResponse>>() {
            @Override
            public void onResponse(Call<List<FindAppointmentListResponse>> call, Response<List<FindAppointmentListResponse>> response) {
                if(!response.isSuccessful()) {
                    try {
                        Log.e("약속 리스트 조회 실패", response.errorBody().string());
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    return;
                }
                List<FindAppointmentListResponse> responseList = response.body();
                if (responseList != null) {
                    for (int i = 0;i < responseList.size(); i++) {
                        FindAppointmentListResponse appointmentResponse = responseList.get(i);
                        appointmentResponse.setName(myAppointmentList.get(i).getName());
                        checkVoteStatus(appointmentResponse);
                    }
                    appointmentList = responseList;
                    setAppointmentList();
                }

            }

            @Override
            public void onFailure(Call<List<FindAppointmentListResponse>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "서버 연결에 실패했습니다.", Toast.LENGTH_SHORT).show();
                Log.e("서버 연결에 실패했습니다.", t.getMessage());
            }
        });

    }

    private void checkVoteStatus(FindAppointmentListResponse appointmentResponse){
        String voteStatus = sharedPreferenceUtil.getData(String.valueOf(appointmentResponse.getId()), "not voted");
        if(voteStatus.equals("voted")){
            appointmentResponse.setFinishVote(true);
        }else{
            appointmentResponse.setFinishVote(false);
        }
    }

    private void setSSE(String key){
        SseEventService sseEventService = new SseEventService();
        try {
            sseEventService.startSse(key);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void setLogoOrList(){
        if (!appointmentList.isEmpty()){
            mainLogo.setVisibility(View.GONE);
            smallLogo.setVisibility(View.VISIBLE);
            appointmentListBox.setVisibility(View.VISIBLE);
        }
    }

    private void setAppointmentList() {
        listAdapter.setList(appointmentList);
        recyclerView.setAdapter(listAdapter);

        setLogoOrList();
    }
}