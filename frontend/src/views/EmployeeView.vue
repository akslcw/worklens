<template>
  <el-card>
    <template #header>
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span>员工管理</span>
        <el-button type="primary" @click="showDialog = true">新增员工</el-button>
      </div>
    </template>
    <el-table :data="employees" style="width:100%">
      <el-table-column prop="id" label="ID" width="80"/>
      <el-table-column prop="name" label="姓名" width="120"/>
      <el-table-column prop="employeeNo" label="工号" width="120"/>
      <el-table-column prop="createdAt" label="创建时间"/>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="$router.push(`/employees/${row.id}`)">查看详情</el-button>
          <el-button type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" title="新增员工" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="姓名">
          <el-input v-model="form.name"/>
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="form.employeeNo"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getEmployees, addEmployee, deleteEmployee } from '../api/employee'
import { ElMessage, ElMessageBox } from 'element-plus'

const employees = ref([])
const showDialog = ref(false)
const form = ref({ name: '', employeeNo: '' })

const load = async () => {
  const res = await getEmployees()
  employees.value = res.data || []
}

const handleAdd = async () => {
  await addEmployee(form.value)
  ElMessage.success('添加成功')
  showDialog.value = false
  form.value = { name: '', employeeNo: '' }
  load()
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确认删除该员工？', '提示', { type: 'warning' })
  await deleteEmployee(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>