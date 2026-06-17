import { createRouter, createWebHistory } from 'vue-router';
import { getSession } from '../session';

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/stations',
    name: 'Stations',
    component: () => import('../views/Stations.vue')
  },
  {
    path: '/user',
    name: 'User',
    component: () => import('../views/User.vue'),
    meta: { requiresAuth: true, roles: ['USER'] }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/Admin.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const session = getSession();
  if (!to.meta?.requiresAuth) {
    return true;
  }
  if (!session.userId) {
    return { name: 'Home', query: { redirect: to.fullPath } };
  }
  if (to.meta.roles?.length && !to.meta.roles.includes(session.role)) {
    return session.role === 'ADMIN' ? { name: 'Admin' } : { name: 'User' };
  }
  return true;
});

export default router;
