package orm;

import annotations.Column;
import annotations.Entity;
import annotations.Id;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class EntityManger<E> implements DBContext<E> {

    private final Connection connection;

    public EntityManger(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean persist(E entity) throws IllegalAccessException, SQLException {
        Field idColumn = getIdColumn(entity.getClass());
        Object idValue = getFieldValue(entity, idColumn);

        if (idValue == null || (long) idValue <= 0) {
            return doInstert(entity);
        }
        return doUpdate(entity, (long) idValue);
    }

    @Override
    public Iterable find(Class table) throws SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return find(table, null);
    }

    @Override
    public Iterable find(Class table, String where) throws NoSuchMethodException, SQLException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return baseFind(table, where, null);

    }

    @Override
    public boolean delete(E toDelete) throws IllegalAccessException, SQLException {
        String tableName = getTableName(toDelete.getClass());

        Field idColumn = getIdColumn(toDelete.getClass());
        idColumn.setAccessible(true);

        String idColumnName = getSqlColumnName(idColumn);
        Object getIdColumnValue = idColumn.get(toDelete);

        String deleteQuery = String.format("DELETE FROM %s WHERE %s =%s", tableName, idColumnName, getIdColumnValue);
        PreparedStatement statement = connection.prepareStatement(deleteQuery);

        return statement.execute();
    }


    @Override
    public Object findFirst(Class table) throws SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return findFirst(table, null);
    }

    @Override
    public Object findFirst(Class table, String where) throws SQLException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String tableName = getTableName(table);

        String selectQuery = String.format("SELECT * FROM %s %s LIMIT 1", tableName, where != null ? "WHERE " + where : "");
        PreparedStatement statement = connection.prepareStatement(selectQuery);

        ResultSet resultSet = statement.executeQuery();
        resultSet.next();

        Object result = table.getDeclaredConstructor().newInstance();
        fillEntity(table, resultSet, (E) result);

        return result;
    }

    private String getTableName(Class<?> aClass) {
        Entity[] annonationsByType = aClass.getAnnotationsByType(Entity.class);//Намиране на името на таблицата
        if (annonationsByType.length == 0) {
            throw new UnsupportedOperationException("Class must be entity");
        }
        return annonationsByType[0].name();
    }

    //Взимане на всички имена на колоно, намиращи се в рамките на SQL
    private Set<String> getSqlColumnNames(Class<E> entityClass) throws SQLException {
        String tableName = getTableName(entityClass);
        Field idColumn = getIdColumn(entityClass);
        String idColumnName = getSqlColumnName(idColumn);

        String schemaQuery = String.format("SELECT COLUMN_NAME FROM information_schema. 'COLUMNS' c" +
                "WHERE c.TABLE_SCHEMA = 'custom_orm" +
                "AND COLUMN_NAME != '%s' " +
                "AND TABLE_NAME = '%s';");

        PreparedStatement statement = connection.prepareStatement(String.format(schemaQuery, idColumnName, tableName));
        ResultSet resultSet = statement.executeQuery();

        Set<String> result = new HashSet<>();
        while (resultSet.next()) {
            String columnName = resultSet.getString("COLUMN_NAME");
            result.add(columnName);
        }
        return result;
    }


    //Създаване на таблица
    public void doCreate(Class<E> entityClass) throws SQLException {
        String tableName = getTableName(entityClass);
        String fieldWithTypes = getSqlFieldWithTypes(entityClass);//Взимане на стойностите в полетата

        String createQuery = String.format("CREATE TABLE %s (" + "id INT PRIMARY KEY, %s)", tableName, fieldWithTypes);

        PreparedStatement statement = connection.prepareStatement(createQuery);
        statement.execute();//Създаване на user table
    }

    //Промяна на съществуваща таблица
    public void doAlter(Class<E> entityClass) throws SQLException {
        String tableName = getTableName(entityClass);
        String addColumnStatements = getAddColumnsStatementsForNewFields(entityClass);

        String alterQuery = String.format("ALTER TABLE %s %s ADD COLUMN", tableName, addColumnStatements);

        PreparedStatement statement = connection.prepareStatement(alterQuery);
        statement.execute();
    }


    private void fillEntity(Class<E> table, ResultSet resultSet, E entity) throws SQLException, IllegalAccessException {
        Field[] declaredFields = table.getDeclaredFields();

        for (Field field : declaredFields) {
            field.setAccessible(true);
            fillFilled(field, resultSet, entity);
        }
    }

    private void fillFilled(Field declaredField, ResultSet resultSet, E entity) throws SQLException, IllegalAccessException {
        Class<?> fieldType = declaredField.getType();
        String fieldName = getSqlColumnName(declaredField);//За всяко поле взимаме името, което е вписано в анотацията

        if (fieldType == int.class || fieldType == Integer.class) {
            int value = resultSet.getInt(fieldName);

            declaredField.set(entity, value);
        } else if (fieldType == long.class || fieldType == Long.class) {
            long value = resultSet.getLong(fieldName);

            declaredField.set(entity, value);
        } else if (fieldType == LocalDate.class) {
            LocalDate value = LocalDate.parse(resultSet.getString(fieldName));

            declaredField.set(entity, value);
        } else {
            String value = resultSet.getString(fieldName);

            declaredField.set(entity, value);
        }

    }


    private boolean doUpdate(E entity, long idValue) throws IllegalAccessException, SQLException {
        String tableName = getTableName(entity.getClass());

        List<String> tableFields = getColumnsWithoutId(entity.getClass());
        List<String> tableValues = getColumnsValuesWithoutId(entity);
        List<String> setStatements = new ArrayList<>();

        for (int i = 0; i < tableFields.size(); i++) {
            String statement = tableFields.get(i) + " = " + tableValues.get(i);
            setStatements.add(statement);
        }
        String updateQuery = String.format("UPDATE %s  SET %s WHERE id = %d", tableName,
                String.join(",", setStatements), idValue);
        PreparedStatement statement = connection.prepareStatement(updateQuery);
        return statement.execute();
    }

    private boolean doInstert(E entity) throws IllegalAccessException, SQLException {
        String tableName = getTableName(entity.getClass());
        List<String> tableFields = getColumnsWithoutId(entity.getClass());
        List<String> tableValues = getColumnsValuesWithoutId(entity);


        String insertQuery = String.format("INSERT INTO %s(%s) VALUES(%s)", tableName,
                String.join(",", tableFields),
                String.join(",", tableValues));

        return connection.prepareStatement(insertQuery).execute();
    }

    private String getSqlColumnName(Field idColumn) {
        return idColumn.getAnnotationsByType(Column.class)[0].name();
    }

    private Object getFieldValue(E entity, Field idColumn) throws IllegalAccessException {
        idColumn.setAccessible(true);
        Object idValue = idColumn.get(entity); //Стойността, която седи в нашата колона
        return idValue;
    }


    private Field getIdColumn(Class<?> clazz) {
        return Arrays.stream(clazz
                        .getDeclaredFields())
                .filter(filter -> filter.isAnnotationPresent(Id.class))
                .findFirst().orElseThrow(() -> new UnsupportedOperationException("Entity missing an id column"));
    }


    private List<String> getColumnsWithoutId(Class<?> aClass) {
        return Arrays.stream(aClass.getDeclaredFields())
                .filter(f -> !f.isAnnotationPresent(Id.class))
                .filter(f -> f.isAnnotationPresent(Column.class))
                .map(f -> f.getAnnotationsByType(Column.class))//Взимане на стойността на тези полета
                .map(a -> a[0].name())
                .collect(Collectors.toList());
    }

    private List<String> getColumnsValuesWithoutId(E entity) throws IllegalAccessException {
        Class<?> aClass = entity.getClass();
        List<Field> fields = Arrays
                .stream(aClass.getDeclaredFields())
                .filter(field -> !field.isAnnotationPresent(Id.class))
                .filter(field -> field.isAnnotationPresent(Column.class))
                .collect(Collectors.toList());

        List<String> values = new ArrayList<>();

        for (Field field : fields) {
            Object o = getFieldValue(entity, field);
            if (o instanceof String || o instanceof LocalDate) {
                values.add("'" + o.toString() + "'"); // 'Mitko'
            } else {
                values.add(o.toString());
            }
        }
        return values;
    }

    private String getSqlFieldWithTypes(Class<E> entityClass) {
        return Arrays.stream(entityClass.getDeclaredFields())//Взимане на полетата
                .filter(f -> !f.isAnnotationPresent(Id.class))//Прескачане на id колоните
                .filter(f -> f.isAnnotationPresent(Column.class))//Взимане само на полетата с Column анотация
                .map(field -> {
                    String fieldName = getSqlColumnName(field);//За всяко поле взимаме името, което е вписано в анотацията
                    String sqlType = getSqlType(field.getType());

                    return fieldName + " " + sqlType;
                }).collect(Collectors.joining(","));

    }

    private static String getSqlType(Class<?> type) {
        String sqlType = "UNKNOWN";
        if (type == Integer.class || type == int.class) {
            sqlType = "INT";
        } else if (type == String.class) {
            sqlType = "VARCHAR(200)";
        } else if (type == LocalDate.class) {
            sqlType = "DATE";
        }
        return sqlType;
    }

    private String getAddColumnsStatementsForNewFields(Class<E> entityClass) throws SQLException {
        Set<String> sqlColumns = getSqlColumnNames(entityClass);

        List<Field> fields = Arrays.stream(entityClass.getDeclaredFields())//Взимане на полетата
                .filter(f -> !f.isAnnotationPresent(Id.class))//Прескачане на id колоните
                .filter(f -> f.isAnnotationPresent(Column.class))
                .collect(Collectors.toList());
        //Взимане само на полетата с Column анотация
        List<String> allAddStatements = new ArrayList<>();


        for (Field field : fields) {
            String fieldName = getSqlColumnName(field);//За всяко поле взимаме името, което е вписано в анотацията
            if (sqlColumns.contains(fieldName)) {
                continue;
            }

            String sqlType = getSqlType(field.getType());
            String addStatement = String.format("ADD COLUMN %s %s", fieldName, sqlType);
            allAddStatements.add(addStatement);
        }

        return String.join(", ", allAddStatements);
    }
    private List<E> baseFind(Class table, String where, String limit) throws SQLException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String tableName = getTableName(table);

        String selectQuery = String.format("SELECT * FROM %s %s %s", tableName, where != null ? "WHERE " + where : "",
                limit != null ? "LIMIT " + limit : "");
        PreparedStatement statement = connection.prepareStatement(selectQuery);

        ResultSet resultSet = statement.executeQuery();


        List<E> resultList = new ArrayList<>();
        while (resultSet.next()) {
            Object entity = table.getDeclaredConstructor().newInstance();
            fillEntity(table, resultSet, (E) entity);

            resultList.add((E) entity);
        }


        return resultList;
    }


}

