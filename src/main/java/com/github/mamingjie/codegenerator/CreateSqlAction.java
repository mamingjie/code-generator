package com.github.mamingjie.codegenerator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtilBase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateSqlAction extends AnAction {

    /**
     * @author Howe
     * @Description 将驼峰转为下划线，正则式
     * @param str  例如：helloWord
     * @return java.lang.String
     * underline:下划线，驼峰：hump
     */
    public static Pattern compile = Pattern.compile("[A-Z]");

    public static String underlineByHump(String str) {

        Matcher matcher = compile.matcher(str);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, (matcher.start() == 0 ? "" : "_") + matcher.group(0).toLowerCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String getStringValue(PsiAnnotationMemberValue annotationValue) {
        return annotationValue.getText().replaceAll("[\"`]", "");
    }

    public static String getFieldType(PsiType psiType, String fieldName) {
        if (psiType instanceof PsiClassReferenceType referenceType) {
            String className = referenceType.getClassName();
            switch (className) {
                case "Integer":
                    return endsWithAny(fieldName.toLowerCase(), "status", "type") ? "tinyint(1)" : "int";
                case "Long":
                    return "bigint";
                case "BigDecimal":
                    return "decimal(10, 2)";
                case "String":
                    return "varchar(255)";
                case "Date":
                    return "datetime";

            }
        }
        return "";
    }

    public static boolean endsWithAny(String str, String... keys) {
        for (String key : keys) {
            if (str.endsWith(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        // Messages.showMessageDialog(project,"Hello,World","First Action", Messages.getInformationIcon());
        // 创建GUI对象
        CreateSqlGenerateForm testDialog = new CreateSqlGenerateForm();
        // 构建对话框
        DialogBuilder dialogBuilder = new DialogBuilder(project);
        // 设置对话框显示内容
        dialogBuilder.setCenterPanel(testDialog.getRootPanel());
        dialogBuilder.setTitle("Create Sql");

        // 获取当前文件对象
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (editor == null || project == null) {
            dialogBuilder.show();
            return;
        }
        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (psiFile == null) {
            dialogBuilder.show();
            return;
        }
        StringBuilder sql = new StringBuilder();
        // 遍历当前对象的所有属性
        for (PsiElement psiElement : psiFile.getChildren()) {
            System.out.println(psiElement);
            PsiAnnotation annotation;
            PsiAnnotationMemberValue annotationValue;
            if (psiElement instanceof PsiClass psiClass) {
                annotation = psiClass.getAnnotation("com.baomidou.mybatisplus.annotation.TableName");
                String tableName = null;
                if (annotation != null) {
                    annotationValue = annotation.findAttributeValue("value");
                    if (annotationValue != null) {
                        tableName = getStringValue(annotationValue);
                    }
                }
                if (tableName == null) {
                    tableName = underlineByHump(psiClass.getName());
                }
                sql.append("create table `").append(tableName).append("` (\n");
                annotation = psiClass.getAnnotation("io.swagger.annotations.ApiModel");
                String tableComment = null;
                if (annotation != null) {
                    annotationValue = annotation.findAttributeValue("description");
                    if (annotationValue != null) {
                        tableComment = getStringValue(annotationValue);
                    } else {
                        annotationValue = annotation.findAttributeValue("value");
                        if (annotationValue != null) {
                            tableComment = getStringValue(annotationValue);
                        }
                    }
                }
                if (tableComment == null) {
                    tableComment = psiClass.getName();
                }
                PsiField[] fields = psiClass.getAllFields();
                String primaryKey = null;
                for (PsiField field : fields) {
                    String fieldName = underlineByHump(field.getName());
                    String fieldType;
                    String fieldComment = null;
                    annotation = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
                    if (annotation != null) {
                        annotationValue = annotation.findAttributeValue("value");
                        if (annotationValue != null) {
                            fieldComment = getStringValue(annotationValue);
                        }
                    }
                    annotation = field.getAnnotation("com.baomidou.mybatisplus.annotation.TableId");
                    if (annotation != null) {
                        annotationValue = annotation.findAttributeValue("value");
                        if (annotationValue != null) {
                            fieldName = getStringValue(annotationValue);
                        }
                        fieldType = getFieldType(field.getType(), fieldName);
                        primaryKey = fieldName;
                        annotationValue = annotation.findAttributeValue("type");
                        boolean autoIncrement = annotationValue != null && getStringValue(annotationValue).contains("AUTO");
                        sql.append("    `").append(fieldName).append("` ").append(fieldType).append(" not null");
                        if (autoIncrement) {
                            sql.append(" auto_increment");
                        }
                        sql.append(" comment '").append(fieldComment == null ? field.getName() : fieldComment).append("',\n");
                    } else {
                        annotation = field.getAnnotation("com.baomidou.mybatisplus.annotation.TableField");
                        if (annotation != null) {
                            annotationValue = annotation.findAttributeValue("exist");
                            if (annotationValue != null && "false".equals(getStringValue(annotationValue))) {
                                continue;
                            }
                            annotationValue = annotation.findAttributeValue("value");
                            if (annotationValue != null) {
                                fieldName = getStringValue(annotationValue);
                            }
                        }
                        fieldType = getFieldType(field.getType(), fieldName);
                        sql.append("    `").append(fieldName).append("` ").append(fieldType);
                        if ("tinyint".equals(fieldType)) {
                            sql.append(" default 0");
                        }
                        if ("createTime".equals(field.getName())) {
                            sql.append(" default current_timestamp");
                        }
                        if ("updateTime".equals(field.getName())) {
                            sql.append(" default current_timestamp on update current_timestamp");
                        }
                        sql.append(" comment '").append(fieldComment == null ? field.getName() : fieldComment).append("',\n");
                    }
                }
                if (primaryKey != null) {
                    sql.append("    primary key (`").append(primaryKey).append("`)\n");
                } else {
                    sql.deleteCharAt(sql.length() - 2);
                }
                sql.append(") comment '").append(tableComment).append("';");
            }
        }
        testDialog.setCodeContentText(sql.toString());
        // 显示对话框
        dialogBuilder.show();
    }

}
