package com.mogomarket.app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// מסך ההרשמה - יצירת חשבון חדש לאפליקציה
public class AuthRegister extends AppCompatActivity {

    // שדות קלט לאימייל וסיסמה
    private EditText editTextEmailAddress, editTextPassword;
    // button = כפתור הרשמה, goToLogIn = כפתור מעבר למסך הלוגין
    private Button button, goToLogIn;
    // אובייקט Firebase Auth
    private FirebaseAuth refAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // תצוגה מקצה לקצה
        setContentView(R.layout.activity_auth_register); // layout של ה-Register

        // ריפוד נכון לפי גודל סרגלי המערכת
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // קישור רכיבי ממשק מה-XML
        editTextEmailAddress = findViewById(R.id.editTextEmailAddress);
        editTextPassword = findViewById(R.id.editTextPassword);
        button = findViewById(R.id.button);
        goToLogIn = findViewById(R.id.GoToLogIn);

        // אתחול Firebase Auth
        refAuth = FirebaseAuth.getInstance();

        // כפתור יצירת משתמש - מפעיל את פונקציית createUser
        button.setOnClickListener(v -> createUser());

        // מעבר למסך לוגין אם יש כבר חשבון
        goToLogIn.setOnClickListener(v -> {
            Intent intent = new Intent(AuthRegister.this, AuthLogin.class);
            startActivity(intent);
        });
    }

    // פונקציה ליצירת משתמש חדש ב-Firebase
    private void createUser() {
        String email = editTextEmailAddress.getText().toString().trim();
        String pass = editTextPassword.getText().toString().trim();

        // וידוא שהשדות לא ריקים
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please fill out all the fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // הצגת חלון טעינה בזמן יצירת המשתמש
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Creating user ...");
        pd.setCancelable(false);
        pd.show();

        // קריאה ל-Firebase ליצירת חשבון חדש עם אימייל וסיסמה
        refAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        pd.dismiss(); // סגירת חלון הטעינה
                        if (task.isSuccessful()) {
                            Log.i("AuthRegister", "createUserWithEmailAndPassword: success");
                            FirebaseUser user = refAuth.getCurrentUser(); // המשתמש שנוצר
                            Toast.makeText(getApplicationContext(),
                                    "User created successfully",
                                    Toast.LENGTH_SHORT).show();

                            // שמירת פרטי התחברות לשימוש עתידי ב-Biometric (לשימוש לימודי, לא לפרודקשן)
                            SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
                            prefs.edit()
                                    .putString("email", email)
                                    .putString("password", pass)
                                    .apply();

                            // מעבר לאפליקציית ההשקעות אחרי הרשמה מוצלחת
                            Intent intent = new Intent(AuthRegister.this, MainActivity.class);
                            startActivity(intent);
                            finish(); // סגירת מסך ההרשמה
                        } else {
                            // כישלון ביצירת המשתמש - בדרך כלל אימייל כבר קיים או סיסמה קצרה מדי
                            Log.e("AuthRegister", "createUserWithEmailAndPassword: failure", task.getException());
                            Toast.makeText(getApplicationContext(),
                                    "Failed to create user",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}