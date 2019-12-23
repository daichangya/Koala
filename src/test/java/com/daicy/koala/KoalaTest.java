package com.daicy.koala;

import com.daicy.koala.exception.MyException;
import com.daicy.koala.structure.Column;
import com.daicy.koala.structure.Row;
import com.daicy.koala.structure.Table;
import com.daicy.koala.structure.impl.TableImpl;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author: create by daichangya
 * @version: v1.0
 * @description: com.daicy.koala
 * @date:19-12-23
 */
public class KoalaTest {

    @Test
    public void testCreateTable() throws MyException {
        Table table = new TableImpl("Student", "name", "roll");
        table.addRow("Spyke", "101");
        table.addRow("Kyle", "102");
        table.addRow("Johnson", "103");
        table.addRow("Johnson", "104");
        List<Row> rows = table.selectRowQuery("name", "Johnson").getSelectedRows();
        for (Row row : rows) {
            for (Column column : row.getColumnList()) {
                System.out.print(column.getColumnName() + "-" + row.getColumnValue(column.getColumnName()) + " # ");
            }
            System.out.println();
        }
        assertEquals(rows.size(), 2);
    }

    @Test(expected = MyException.class)
    public void testExcessAddingColumn() throws MyException {

        Table table = new TableImpl("Student", "name", "roll");
        table.addRow("Spyke", "101");
        table.addRow("Kyle", "102");
        table.addRow("Johnson", "103");
        table.addRow("Johnson", "104", "error");
    }

    @Test
    public void testAddIndexing() throws MyException {

        Table table = new TableImpl("Student", "name", "roll");
        table.addRow("Spyke", "101");
        table.addRow("Kyle", "102");
        table.addRow("Johnson", "103");
        table.createIndex("name");
    }

    @Test(expected = MyException.class)
    public void testRemoveIndexingWithException() throws MyException {
        Table table = new TableImpl("Student", "name", "roll");
        table.addRow("Spyke", "101");
        table.addRow("Kyle", "102");
        table.addRow("Johnson", "103");
        table.deleteIndex("name");
    }

    @Test
    public void testRemoveIndexing() throws MyException {
        Table table = new TableImpl("Student", "name", "roll");
        table.addRow("Spyke", "101");
        table.addRow("Kyle", "102");
        table.addRow("Johnson", "103");
        table.createIndex("name");
        table.deleteIndex("name");
    }

    @Test
    public void voidjoinTable() throws MyException {
        Table student = new TableImpl("Student", "name", "roll");
        student.addRow("Spyke", "101");
        student.addRow("Kyle", "102");
        student.addRow("Johnson", "103");

        Table marks = new TableImpl("Marks", "marks", "roll");
        marks.addRow(13.4, "101");
        marks.addRow(23.5, "102");
        marks.addRow(44.3, "103");

        List<Row> rows = student.joinTable(marks, "roll", "roll").getSelectedRows();
        for (Row row : rows) {
            for (Column column : row.getColumnList()) {
                System.out.print(column.getColumnName() + "-" + row.getColumnValue(column.getColumnName()) + " # ");
            }
            System.out.println();
        }
    }

    @Test
    public void joinTableWithReoccuringKey() throws MyException {
        Table student = new TableImpl("Student", "name", "roll");
        student.addRow("Spyke", "101");
        student.addRow("Kyle", "102");
        student.addRow("Johnson", "103");

        Table marks = new TableImpl("Marks", "marks", "roll");
        marks.addRow(13.4, "101");
        marks.addRow(23.5, "103");
        marks.addRow(44.3, "105");

        List<Row> rows = student.joinTable(marks, "roll", "roll").getSelectedRows();
        for (Row row : rows) {
            for (Column column : row.getColumnList()) {
                System.out.print(column.getColumnName() + "-" + row.getColumnValue(column.getColumnName()) + " # ");
            }
            System.out.println();
        }

    }

    @Test
    public void testGetbyColumnName() throws MyException {
        Table student = new TableImpl("Student", "name", "roll", "marks");
        student.addRow("Spyke", "101", 43.3);
        student.addRow("Kyle", "102", 44.5);
        student.addRow("Johnson", "103", 65.6);
        student.addRow("Johny", "103", 43.3);

        for (Row row : student.selectRowQuery("roll", "103").getSelectedRows()) {
            System.out.println("Name = " + row.getColumnValue("roll"));
        }

    }

    @Test
    public void tableCreateWithMultiThread() throws MyException, InterruptedException {
        Thread[] t = new Thread[100];

        for (int i = 0; i < 100; i++) {
            t[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Table tab = new TableImpl(null, "name", "roll", "marks", "dob");
                        for (int i = 0; i < 10000; i++) {
                            tab.addRow("arg1", 101, 43.76, new Date());
                        }
                        tab.createIndex("name");
                        tab.createIndex("roll");
                        tab.createIndex("marks");
                        tab.createIndex("dob");
                    } catch (MyException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        for (int i = 0; i < 100; i++) {
            t[i].start();
        }
        for (int i = 0; i < 100; i++) {
            t[i].join();
        }
    }

    @Test
    public void multiOperationOnSingleTable() throws MyException, InterruptedException {
        final String[] args = {"name", "roll", "marks", "dob", "col1", "col2", "col3", "col4"};
        final Table tab = new TableImpl(null, args);
        for (int i = 0; i < 100000; i++) {
            tab.addRow("arg1", 101, 43.76, new Date(), 123, "213123", "12", true);
        }
        Thread[] t = new Thread[8];

        for (int i = 0; i < 8; i++) {
            t[i] = new Thread(new Parallen(tab, i, args));
        }
        for (int i = 0; i < 8; i++) {
            t[i].start();
        }
        for (int i = 0; i < 8; i++) {
            t[i].join();
        }


    }

    class Parallen implements Runnable {

        Table tab;
        int i;
        String[] args;

        Parallen(Table t, int x, String[] args) {
            this.tab = t;
            this.i = x;
            this.args = args;
        }

        @Override
        public void run() {
            try {
                if (i % 2 == 0)
                    tab.createIndex(args[i]);
                else
                    tab.deleteIndex(args[i - 1]);

            } catch (MyException e) {
                e.printStackTrace();
            }
        }
    }
}