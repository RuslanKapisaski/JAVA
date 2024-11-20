import entities.Address;
import entities.User;
import orm.EntityManger;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;


import static orm.MyConnector.createConnection;
import static orm.MyConnector.getConnection;

public class Main {
    public static void main(String[] args) throws SQLException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
//        createConnection("postgres", "123r", "custom_orm");
//        Connection connection = getConnection();
//
//        EntityManger<User> userEntityManager = new EntityManger<>(connection);
//        EntityManger<Address> addressEntityManger = new EntityManger<>(connection);
//
//
//        User myUser = new User("Dimitar", 25, LocalDate.now());
//
//        userEntityManager.persist(myUser);
//              userEntityManager.doCreate(User.class);
//    userEntityManager.doAlter(User.class);


//        Iterable<User> first = userEntityManager.find(User.class);
//
//
//        System.out.println(first.toString());

//        userEntityManager.delete(myUser);

//        addressEntityManger.doCreate(Address.class); //Създаване на адрес таблица в БД

//        connection.close();
    }
}
