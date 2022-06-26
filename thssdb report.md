# thssdb report

## 查询模块

* CREATE TABLE

  * 功能演示

    ```sql
    CREATE TABLE person (name String(256), ID Int not null, PRIMARY KEY(ID))
    ```

    ![image-20220626101551132](https://github.com/Tokiwa-17/thssdb/blob/master/thssdb%20report/image-20220626101551132.png)

  * 实现方法

    修改`impVisitor.java`文件。

    首先通过`ctx.table_name()`获取表的名字，然后创建一个新的`Column`ArrayList，使用`ctx.getChild(i)`语句对每个数据项或主键进行分析，如果是数据项，提取`ColumnName`和`typeName`(长度: 默认128)，对该数据项的约束进行分析，记录是否有`NOT NULL`约束。扫描完元数据的信息后调用`new Column(columnName, type, 0, notNull, length)`将该Column的信息加入到ArrayList中。最后如果是主键约束，从ArrayList中扫描所有Column，将该Column的主键改为1。

    最后将ArrayList转为Column数组，调用`GetCurrentDB().create(tablename, columns)`成功创建table，返回创建成功的信息。

  
