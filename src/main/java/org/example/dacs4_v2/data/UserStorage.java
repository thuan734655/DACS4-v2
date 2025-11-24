package org.example.dacs4_v2.data;

import org.example.dacs4_v2.models.User;

public class UserStorage {

    private static final String USER_FILE = "user.json";

    public static void saveUser(User user) {
        if (user == null) return;
        DataStorage.save(user, USER_FILE);
    }
}
