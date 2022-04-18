package com.google.mlkit.vision.demo.java.chatpapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.mlkit.vision.demo.R;

import de.hdodenhof.circleimageview.CircleImageView;

public class RegisterActivity extends AppCompatActivity {

    CircleImageView profileImage;
    EditText regName, regEmail, regPassword, regAge;
    FirebaseAuth auth;
    Uri imageUri;
    Button signUpBtn;
    FirebaseDatabase database;
    FirebaseStorage storage;
    String imageURI;
    ProgressDialog progressDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        progressDialog=new ProgressDialog(this);
        progressDialog.setMessage("Please Wait");
        progressDialog.setCancelable(false);

        auth=FirebaseAuth.getInstance();
        database=FirebaseDatabase.getInstance();
        storage=FirebaseStorage.getInstance();
        signUpBtn=findViewById(R.id.register_user);
        profileImage=findViewById(R.id.profile_image);
        regEmail=findViewById(R.id.register_email);
        regName=findViewById(R.id.register_name);
        regPassword=findViewById(R.id.register_password);
        regAge=findViewById(R.id.register_age);

        signUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressDialog.show();
                String name=regName.getText().toString();
                String email=regEmail.getText().toString();
                String password=regPassword.getText().toString();
                String age=regAge.getText().toString();


                if(TextUtils.isEmpty(name)||TextUtils.isEmpty(email)||TextUtils.isEmpty(password)||TextUtils.isEmpty(age))
                {
                    progressDialog.dismiss();
                    Toast.makeText(RegisterActivity.this, "Please Enter Valid Data", Toast.LENGTH_SHORT).show();
                }
                else if(password.length()<6)
                {
                    progressDialog.dismiss();
                    Toast.makeText(RegisterActivity.this, "Minimum Length=6", Toast.LENGTH_LONG).show();
                }
                else{
                    auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if(task.isSuccessful())
                            {

                                Toast.makeText(RegisterActivity.this, "User created succcessfully", Toast.LENGTH_SHORT).show();
                                DatabaseReference reference=database.getReference().child("user").child(auth.getUid());
                                StorageReference storageReference=storage.getReference().child("upload").child(auth.getUid());
                                if (imageUri!=null)
                                {
                                    storageReference.putFile(imageUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                                            if(task.isSuccessful())
                                            {
                                                storageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                                    @Override
                                                    public void onSuccess(Uri uri) {
                                                        imageURI=uri.toString();
                                                        Users user=new Users(auth.getUid(),name,email,age,imageURI);
                                                        reference.setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<Void> task) {
                                                                if(task.isSuccessful())
                                                                {
                                                                    progressDialog.dismiss();
                                                                    startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                                                                }else{
                                                                    Toast.makeText(RegisterActivity.this, "Error in creating User.", Toast.LENGTH_SHORT).show();
                                                                }
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        }
                                    });
                                }
                                else{
                                    progressDialog.dismiss();
                                    imageURI="https://firebasestorage.googleapis.com/v0/b/simplestance2.appspot.com/o/userPhoto.png?alt=media&token=c7ab818b-aa05-4a4a-88f7-d2b1b035e465";
                                    Users user=new Users(auth.getUid(),name,email,age,imageURI);
                                    reference.setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            if(task.isSuccessful())
                                            {
                                                startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                                            }else{
                                                Toast.makeText(RegisterActivity.this, "Error in creating User.", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });
                                }
                            }
                            else{
                                Toast.makeText(RegisterActivity.this, "Something went wrong", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }

                profileImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent, "Select Picture"), 10);
                    }
                });
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode==10)
        {
            if(data!=null)
            {
                imageUri=data.getData();
                profileImage.setImageURI(imageUri);
            }
        }
    }
}