package com.daicy.koala.structure.impl;

import com.daicy.koala.BpTree;
import com.daicy.koala.exception.MyException;
import com.daicy.koala.indexer.BasicIndexQuery;
import com.daicy.koala.structure.Column;
import com.daicy.koala.structure.Row;
import com.daicy.koala.structure.Table;
import org.apache.calcite.sql.SqlKind;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by amd on 9/9/15.
 */
public class TableImpl implements Table {

    private static final String ID_COLUMN_DEFAULT_EXCEPTION = "id column is by default present";
    private static final String NO_SUCH_COLUMN_EXIST = "No such column exist by name : ";
    private static final String COLUMN_MISMATCH = "No of elemtnts to insert does not match with column number";
    private static final String INDEXING_ALREADY_EXIST = "Indexing already exist for column : ";
    private static final Object INDEXING_DOESNOT_EXIST = "Indexing doesnot exist for column : ";
    private static final String DEFAULT_INDEXING_CANNOT_REMOVE = "Cannot remove default indexing for column : ";
    private static final String DUPLICATE_ID_VALUE_NOT_ALLOWED = "ID Value already exist for other column ";

    private final int INDEX_POSITION = 0;
    private final String INDEX_COLUMN = "id";

    private AtomicLong id = new AtomicLong(0);

    String tableName;
    List<Column> columnList;

    HashMap<Long, Row> rows;
    HashMap<String, SortedMap> indexMap;


    public TableImpl(String tableName, String... columns) throws MyException {
        this.tableName = tableName;
        this.columnList = new LinkedList<Column>();
        this.rows = new HashMap<Long, Row>();
        this.indexMap = new HashMap<String, SortedMap>();

        int position = 0;
        columnList.add(new ColumnImpl(INDEX_COLUMN, "Integer", null, false, true, position));

        for (String column : columns) {
            if (column.equalsIgnoreCase(INDEX_COLUMN) == false) {
                position++;
                Column c = new ColumnImpl(column.toLowerCase(), "String", null, false, false, position);
                columnList.add(c);
            } else {
                throw new MyException(ID_COLUMN_DEFAULT_EXCEPTION);
            }
        }
        createIndex(INDEX_COLUMN);
    }


    @Override
    public Table addRow(Object... args) throws MyException {

        synchronized (this) {
            if ((args.length + 1) != getNumberOfColumn())
                throw new MyException(COLUMN_MISMATCH);

            Long id = incrementID();
            RowImpl row = new RowImpl(columnList);
            List<Object> object = new LinkedList<Object>();
            object.add(id);

            for (int i = 0; i < args.length; i++)
                object.add(args[i]);

            row.addRow(object);
            this.rows.put(id, row);
            applyIndexing(id);
            return this;
        }
    }

    private Table addNewRow(Row row) throws MyException {

        synchronized (this) {
            List<Object> object = row.getAllColumnValue().subList(INDEX_POSITION + 1, row.getColumnList().size());
            return addRow(object.toArray());
        }
    }

    @Override
    public Table selectRowQuery(String columnName, Object value) throws MyException {
        return this.selectRowQuery(SqlKind.EQUALS,columnName, value);
    }

    @Override
    public Table selectRowQuery(SqlKind sqlKind,Object... value) throws MyException {
        if (value.length < 1 || null == value[0] || StringUtils.isEmpty(value[0].toString())) {
            throw new MyException(ID_COLUMN_DEFAULT_EXCEPTION);
        }
        String columnName = value[0].toString().toLowerCase();
        List<Integer> ids = new LinkedList<Integer>();
        int position = -1;

        for (Column column : columnList) {
            if (column.getColumnName().equalsIgnoreCase(columnName))
                position = column.getPosition();
        }

        // if indexing is present on column
        if (indexMap.get(columnName) != null) {
            if (SqlKind.EQUALS.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionEQ(this.indexMap, value).getIdList();
            } else if (SqlKind.NOT_EQUALS.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionNE(this.indexMap, value).getIdList();
            } else if (SqlKind.GREATER_THAN.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionGT(this.indexMap, value).getIdList();
            }else if (SqlKind.GREATER_THAN_OR_EQUAL.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionGE(this.indexMap, value).getIdList();
            }else if (SqlKind.LESS_THAN.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionLT(this.indexMap, value).getIdList();
            }else if (SqlKind.LESS_THAN_OR_EQUAL.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionLE(this.indexMap, value).getIdList();
            }else if (SqlKind.BETWEEN.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionBW(this.indexMap, value).getIdList();
            }else if (SqlKind.IN.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionIN(this.indexMap, value).getIdList();
            }else if (SqlKind.NOT_IN.equals(sqlKind)) {
                ids = new BasicIndexQuery.IndexConditionNIN(this.indexMap, value).getIdList();
            }
        }
        // else search normal
        else {
            for (Row row : rows.values()) {
                if (row.getColumnValue(position).equals(value))
                    ids.add((Integer) row.getColumnValue(INDEX_POSITION));
            }
        }
        List<String> columnNames = new ArrayList<String>();
        for (Column column : columnList) {
            if (!column.getColumnName().equalsIgnoreCase(INDEX_COLUMN))
                columnNames.add(column.getColumnName());
        }
        TableImpl retVal = new TableImpl(null, columnNames.toArray(new String[columnNames.size()]));
        for (Object id : ids) {
            retVal = (TableImpl) retVal.addNewRow(rows.get(id));
        }

        return retVal;
    }

    private Long incrementID() {
        return id.getAndIncrement();
    }

    private int getNumberOfColumn() {
        //return columnList.get(INDEX_POSITION).getIsId()?-1:columnList.size();
        return columnList.size();
    }

    @Override
    public Table joinTable(Table table, String columnFormerTable, String columnLaterTable) throws MyException {

        Map<Integer, Row> rows;

        TableImpl table1 = this;
        TableImpl table2 = (TableImpl) table;

        Column column1 = table1.getColumnByName(columnFormerTable);
        Column column2 = table2.getColumnByName(columnLaterTable);

        List<String> newColumnNames = new LinkedList<String>();
        for (Column c : table1.getColumnList()) {
            if (!c.getIsId())
                newColumnNames.add(c.getColumnName().toLowerCase());
        }
        for (Column c : table2.getColumnList()) {
            if (!newColumnNames.contains(c.getColumnName().toLowerCase()) && !c.getIsId())
                newColumnNames.add(c.getColumnName().toLowerCase());
        }

        TableImpl newtable = new TableImpl(table1.getTableName() + "." + table2.getTableName(), newColumnNames.toArray(new String[newColumnNames.size()]));

        Map<Object, List<Row>> mapper1 = new HashMap<Object, List<Row>>();
        Map<Object, List<Row>> mapper2 = new HashMap<Object, List<Row>>();

        for (Row row : table1.getSelectedRows()) {
            Object key = row.getColumnValue(column1.getPosition());
            List<Row> value = mapper1.getOrDefault(key, new LinkedList<Row>());
            value.add(row);
            mapper1.put(key, value);
        }

        for (Row row : table2.getSelectedRows()) {
            Object key = row.getColumnValue(column2.getPosition());
            List<Row> value = mapper2.getOrDefault(key, new LinkedList<Row>());
            value.add(row);
            mapper2.put(key, value);
        }

        for (Map.Entry<Object, List<Row>> entrySet : mapper1.entrySet()) {
            Object key = entrySet.getKey();
            for (Row row1 : entrySet.getValue()) {
                for (Row row2 : mapper2.getOrDefault(key, Collections.<Row>emptyList())) {

                    List<Object> rowData = joinRows(row1, row2);
                    newtable.addRow(rowData.toArray());

                }
            }
        }

        return newtable;
    }


    @Override
    public void createIndex(String columnName) throws MyException {
        synchronized (this) {
            ColumnImpl column = (ColumnImpl) getColumnByName(columnName);
            if (column.getIsIndexed())
                throw new MyException(INDEXING_ALREADY_EXIST + columnName);

            if (column.getColumnName().equalsIgnoreCase(columnName) && !column.getIsIndexed()) {
                int position = column.getPosition();

                SortedMap<Object, List<Integer>> map = new BpTree<>(10, 3);

                for (Row row : rows.values()) {
                    Object key = row.getColumnValue(position);
                    List value = map.getOrDefault(key, new LinkedList());
                    value.add(row.getColumnValue(INDEX_POSITION));
                    map.put(key, value);
                }
                indexMap.put(column.getColumnName(), map);
                column.setIsIndexed(true);
            }
        }
    }

    @Override
    public void deleteIndex(String columnName) throws MyException {
        synchronized (this) {
            ColumnImpl column = (ColumnImpl) getColumnByName(columnName);
            if (!column.getIsIndexed())
                throw new MyException(INDEXING_DOESNOT_EXIST + columnName);
            if (column.getColumnName().equalsIgnoreCase(INDEX_COLUMN))
                throw new MyException(DEFAULT_INDEXING_CANNOT_REMOVE + columnName);
            indexMap.remove(column.getColumnName());
            column.setIsIndexed(false);
        }
    }

    private void applyIndexing(Long id) throws MyException {

        Row row = rows.get(id);

        for (Column column : columnList) {
            if (column.getIsIndexed()) {
                SortedMap map = indexMap.get(column.getColumnName());
                Object key = row.getColumnValue(column.getPosition());
                List value = (List) map.getOrDefault(key, new LinkedList());
                value.add(id);
                map.put(key, value);
            }
        }
    }

    public Column getColumnByName(String columnName) throws MyException {
        for (Column column : columnList) {
            if (column.getColumnName().equalsIgnoreCase(columnName))
                return column;
        }

        throw new MyException(NO_SUCH_COLUMN_EXIST + columnName);
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public List<Row> getSelectedRows() {
        return new LinkedList<Row>(this.rows.values());
    }

    private HashMap getRows() {
        return this.rows;
    }

    public List<Column> getColumnList() {
        return columnList;
    }

    public void setColumnList(List<Column> columnList) {
        this.columnList = columnList;
    }

    private boolean isRowCompatible(Row row) {

        if (this.columnList.size() == row.getColumnList().size()) {
            for (int i = 0; i < this.columnList.size(); i++) {
                if (this.columnList.get(i).getColumnName().equalsIgnoreCase(row.getColumnList().get(i).getColumnName())) {

                } else return false;

            }
            return true;
        }
        return false;
    }

    private List<Object> joinRows(Row row1, Row row2) throws MyException {

        List<String> columns1 = new LinkedList<String>();
        List<String> columns2 = new LinkedList<String>();
        for (Column c : row1.getColumnList()) {
            if (!c.getIsId())
                columns1.add(c.getColumnName().toLowerCase());
        }
        for (Column c : row2.getColumnList()) {
            if (!c.getIsId() && !columns1.contains(c.getColumnName().toLowerCase()))
                columns2.add(c.getColumnName().toLowerCase());
        }

        List<Object> newObject = new LinkedList<Object>();
        for (String c : columns1) {
            newObject.add(row1.getColumnValue(c));
        }
        for (String c : columns2) {
            newObject.add(row2.getColumnValue(c));
        }
        return newObject;

    }

}
