# 多阶段构建 - 构建阶段
FROM --platform=$BUILDPLATFORM node:20.19.0 AS builder

WORKDIR /app

COPY package*.json ./
# 上游依赖用 ^ 版本，新发布的 @heroui 子包要求更高的 @heroui/theme，严格 peer 校验会失败
RUN npm install --legacy-peer-deps

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:stable-alpine AS production-stage

COPY nginx.conf /etc/nginx/nginx.conf

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"] 