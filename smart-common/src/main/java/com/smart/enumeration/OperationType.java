package com.smart.enumeration;

/**
 * 数据库操作类型
 * 作用：使代码更加清晰和类型安全，避免使用字符串或整数来表示操作类型可能带来的错误。在涉及数据库操作的代码中，可以使用 OperationType 枚举来表示操作类型
 * 用例：在数据访问层的方法参数中，或者在业务逻辑中根据操作类型进行不同的处理。
 */
public enum OperationType {

    /**
     * 更新操作
     */
    UPDATE,

    /**
     * 插入操作
     */
    INSERT

}
