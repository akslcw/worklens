<template>
  <el-card>
    <template #header>
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span>应用分类管理</span>
        <el-button type="primary" @click="showDialog = true">新增分类</el-button>
      </div>
    </template>

    <el-table :data="categories" style="width:100%">
      <el-table-column prop="appName" label="应用名称" width="180"/>
      <el-table-column prop="category" label="分类" width="120"/>
      <el-table-column label="是否工作应用" width="120">
        <template #default="{ row }">
          <el-tag :type="row.isWork ? 'success' : 'danger'">
            {{ row.isWork ? '工作' : '非工作' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" :title="form.id ? '编辑分类' : '新增分类'" width="400px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="应用名称">
          <el-input v-model="form.appName" placeholder="如 idea64.exe"/>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width:100%">
            <el-option label="开发工具" value="开发工具"/>
            <el-option label="通讯" value="通讯"/>
            <el-option label="浏览器" value="浏览器"/>
            <el-option label="娱乐" value="娱乐"/>
            <el-option label="其他" value="其他"/>
          </el-select>
        </el-form-item>
        <el-form-item label="是否工作应用">
          <el-switch v-model="form.isWork"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCategories, addCategory, updateCategory, deleteCategory } from '../api/category'
import { ElMessage, ElMessageBox } from 'element-plus'

const categories = ref([])
const showDialog = ref(false)
const form = ref({ id: null, appName: '', category: '开发工具', isWork: true })

const load = async () => {
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch (e) {
    ElMessage.error('分类数据加载失败')
  }
}

const handleEdit = (row) => {
  form.value = { ...row }
  showDialog.value = true
}

const handleClose = () => {
  showDialog.value = false
  form.value = { id: null, appName: '', category: '开发工具', isWork: true }
}

const handleSubmit = async () => {
  try {
    if (form.value.id) {
      await updateCategory(form.value)
      ElMessage.success('更新成功')
    } else {
      await addCategory(form.value)
      ElMessage.success('添加成功')
    }
    handleClose()
    load()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该分类？', '提示', { type: 'warning' })
    await deleteCategory(id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(load)
</script>