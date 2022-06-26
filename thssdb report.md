# thssdb report

## 查询模块

* CREATE TABLE

  * 功能演示

    ```sql
    CREATE TABLE person (name String(256), ID Int not null, PRIMARY KEY(ID))
    ```

    ![image-20220626101551132](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626101551132.png)

  * 实现方法

    修改`impVisitor.java`文件的`visitCreate_table_stmt`函数。

    首先通过`ctx.table_name()`获取表的名字，然后创建一个新的`Column`ArrayList，使用`ctx.getChild(i)`语句对每个数据项或主键进行分析，如果是数据项，提取`ColumnName`和`typeName`(长度: 默认128)，对该数据项的约束进行分析，记录是否有`NOT NULL`约束。扫描完元数据的信息后调用`new Column(columnName, type, 0, notNull, length)`将该Column的信息加入到ArrayList中。最后如果是主键约束，从ArrayList中扫描所有Column，将该Column的主键改为1。

    最后将ArrayList转为Column数组，调用`GetCurrentDB().create(tablename, columns)`成功创建table，返回创建成功的信息。

* DROP TABLE

  * 功能演示

    ```sql
    DROP TABLE person;
    ```

    ![image-20220626103319501](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626103319501.png)

  * 实现方法

    修改`impVisitor.java`文件的`visitDrop_table_stmt`函数。

    在try语句块中调用

    ```java
    GetCurrentDB().drop(ctx.table_name().getText().toLowerCase());
    ```

* SHOW TABLE

  * 功能演示：

    ```sql
    SHOW TABLE person;
    ```

  * 实现方法

    修改`impVisitor.java`文件的`visitShow_meta_stmt`函数。

    每一行展示一个字段的信息。

    首先获取表名，然后对于每个Column, 调用`column.getColumnName()`和`column.getColumnType()`获取Column的名字和type, `column.getMaxLength()`获取最大长度。然后通过`columns.get(i).isPrimary()`和`columns.get(i).cantBeNull()`判断该column的约束，最后返回上述metadata结果。
    
    
- UPDATE 
   - 功能演示：

  ```sql
  UPDATE  tableName  SET  attrName = attrValue  WHERE  attrName = attrValue
  ```

  ![1656216553432](thssdb report/1656216553432.png)

    - 实现方法

      修改`impVisitor.java`文件的`visitUpdate_stmt`函数。

       首先获取表名并拿到对应的表，根据UPDATE后面的WHERE字句，从列信息中找到表中对应的属性，并将WHERE子句等号右边的值转化为对应的类型。然后将每一行里这个属性的值与其作比较，来筛选出表中符合条件的行。最后对每一行都调用`table.update`来更新这一行。
   
- SELECT

   * 功能演示：

    ```sql
   SELECT tableName1.AttrName1, tableName1.AttrName2…, tableName2.AttrName1, tableName2.AttrName2,…  FROM  tableName1 [JOIN tableName2 [ON  tableName1.attrName1 = tableName2.attrName2]] [ WHERE  attrName1 = attrValue ]
    ```

  ![1656216334762](thssdb report/1656216334762.png)

  * 实现方法

    定义`QueryTable`来保存查询中途的表的行列信息，同时实现`QueryTable·`之间的Join。

    修改`impVisitor.java`文件的`visitSelect_stmt`函数。
  
    先处理FROM字句，拿到对应的QueryTable，
    
    处理方法为：
    
    - 若有至少一个Join，就将最后一个Join前面的部分递归处理，然后将处理结果与最后一个join后面的表名对应的QueryTable进行join。
    - 否则，只剩下一个表名需要处理，返回它对应的QueryTable
  - 注意，利用一个表名得到对应的QueryTable时，会将表名加在列信息里每个属性的前面。
    
    因此当前的SELECT语句是可以支持多个JOIN的。
    
    
    
    接下来处理WHERE子句，即从原来的QueryTable中筛选出一些行，得到一个新的QueryTable。
    
    处理方法为：利用与Update中相同的方法筛选出刚才拿到的QueryTable中满足条件的所有行。
    
    
    
    最后处理SELECT子句，即从原来的QueryTable中筛选出一些列，得到一个新的QueryTable。
    
    处理方法为：得到每个最终要查询的属性在之前的QueryTable中对应的索引。然后对每一行，筛选出这些索引对应的列即可。
    
    
    
    处理完后，将结果保存至QueryResult里。
  

## 事务模块

* 实现READ COMMITTED隔离级别

  * 功能演示

    准备数据

    ```sql
    create table bank(id int, name String(256) NOT NULL, balance int NOT NULL, PRIMARY KEY(id));
    insert into bank values(1, 'Alice', 2000);
    insert into bank values(2, 'Bob', 2000);
    select bank.id, bank.name, bank.balance from bank;
    ```

    ![image-20220626105130541](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626105130541.png)

    开两个客户端A, B:

    客户端A:

    ```sql
    begin transaction;
    update bank set balance=1000 where name='Alice';
    ```

    ![image-20220626105315340](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626105315340.png)

    客户端B:

    无法读取uncommitted data。

    ```sql
    select bank.id, bank.name, bank.balance from bank;
    ```

    ![image-20220626110052436](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626110052436.png)

    客户端A commit

    ![image-20220626110132378](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626110132378.png)

    客户端B 可以正确读取 
    
    ![image-20220626110205663](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626110205663.png)

