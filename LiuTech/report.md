# 🌸 屎山代码分析报告 🌸

F:\工程代码\Liutech\LiuTech>fuck-u-code analyze --verbose
🔍 开始嗅探：.
🔍 正在搜索源代码文件......
📂 已找到文件数: 83
  正在分析文件: 14/83 [█████░░░░░░░░░░░░░░░░░░░░░░░░░]

────────────────────────────────────────────────────────────────────────────────ValidationUtil.java

  🌸 屎山代码分析报告 🌸
────────────────────────────────────────────────────────────────────────────────

  总体评分: 32.53 / 100 - 有点臭味，但还不至于熏死人
  屎山等级: 偶有异味 - 基本没事，但是有伤风化


◆ 评分指标详情

✓✓ 注释覆盖率                   3.25分            注释不错，能靠它活下来
✓✓ 状态管理                     11.87分           状态管理清晰，变量作用域合理，状态可预测
 ✓ 错误处理                     25.00分           有处理，但处理得跟没处理一样
 ✓ 代码结构                     30.00分           结构还行，但有点混乱
 ○ 代码重复度                  35.00分           有点重复，抽象一下不难吧
 ⚠ 循环复杂度                   63.49分           函数像迷宫，维护像打副本

  评分计算: (3.25×0.15 + 11.87×0.20 + 25.00×0.10 + 30.00×0.15 + 35.00×0.15 + 63.49×0.30) ÷ 1.05 = 32.53


◆ 全部代码文件分析

  1. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\UserProfileService.java(屎气指数: 48.35)
     🔄 复杂度问题: 10   ⚠️  其他问题: 3

     🔄 函数 updateProfile 的循环复杂度过高 (17)，考虑重构
     🔄 函数 getCurrentUserStats 的循环复杂度过高 (17)，考虑重构
     🔄 函数 getProfile 的循环复杂度过高 (17)，考虑重构
     🔄 函数 getDefaultProfile 的循环复杂度过高 (17)，考虑重构
     🔄 函数 getCurrentUserId 的循环复杂度过高 (17)，考虑重构
     ⚠️  函数 'updateProfile' () 较长 (55 行)，可考虑重构
     🔄 函数 'updateProfile' () 复杂度过高 (17)，建议简化
     ⚠️  函数 'getCurrentUserStats' () 较长 (52 行)，可考虑重构
     🔄 函数 'getCurrentUserStats' () 复杂度过高 (17)，建议简化
     ⚠️  函数 'getProfile' () 较长 (56 行)，可考虑重构
     🔄 函数 'getProfile' () 复杂度过高 (17)，建议简化
     🔄 函数 'getDefaultProfile' () 复杂度过高 (17)，建议简化
     🔄 函数 'getCurrentUserId' () 复杂度过高 (17)，建议简化

  2. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\CommentsService.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  3. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\admin\CategoriesAdminController.java(屎气 指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  4. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\FileUploadService.java(屎气指数: 45.95)
     🔄 复杂度问题: 14

     🔄 函数 uploadImage 的循环复杂度较高 (15)，建议简化
     🔄 函数 uploadDocument 的循环复杂度较高 (15)，建议简化
     🔄 函数 uploadResource 的循环复杂度较高 (15)，建议简化
     🔄 函数 validateUser 的循环复杂度较高 (15)，建议简化
     🔄 函数 validateImageFile 的循环复杂度较高 (15)，建议简化
     🔄 函数 validateDocumentFile 的循环复杂度较高 (15)，建议简化
     🔄 函数 validateResourceFile 的循环复杂度较高 (15)，建议简化
     🔄 函数 'uploadImage' () 复杂度过高 (15)，建议简化
     🔄 函数 'uploadDocument' () 复杂度过高 (15)，建议简化
     🔄 函数 'uploadResource' () 复杂度过高 (15)，建议简化
     🔄 函数 'validateUser' () 复杂度过高 (15)，建议简化
     🔄 函数 'validateImageFile' () 复杂度过高 (15)，建议简化
     🔄 函数 'validateDocumentFile' () 复杂度过高 (15)，建议简化
     🔄 函数 'validateResourceFile' () 复杂度过高 (15)，建议简化

  5. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\utils\BeanConvertUtil.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  6. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\UserManagementService.java(屎气指数: 45.95)
     🔄 复杂度问题: 48

     🔄 函数 BCryptPasswordEncoder 的循环复杂度过高 (23)，考虑重构
     🔄 函数 getCurrentUserEntity 的循环复杂度过高 (23)，考虑重构
     🔄 函数 convertToUserResl 的循环复杂度过高 (23)，考虑重构
     🔄 函数 getUsersByCondition 的循环复杂度过高 (23)，考虑重构
     🔄 函数 getUserById 的循环复杂度过高 (23)，考虑重构
     🔄 函数 getUsersByUsername 的循环复杂度过高 (23)，考虑重构
     🔄 函数 getAllUsers 的循环复杂度过高 (23)，考虑重构
     🔄 函数 getUserListForAdmin 的循环复杂度过高 (23)，考虑重构
     🔄 函数 validatePaginationParams 的循环复杂度过高 (23)，考虑重构
     🔄 函数 queryUsersForAdmin 的循环复杂度过高 (23)，考虑重构
     🔄 函数 buildPageResult 的循环复杂度过高 (23)，考虑重构
     🔄 函数 saveUser 的循环复杂度过高 (23)，考虑重构
     🔄 函数 preprocessUserForSave 的循环复杂度过高 (23)，考虑重构
     🔄 函数 updateUserById 的循环复杂度过高 (23)，考虑重构
     🔄 函数 preprocessUserForUpdate 的循环复杂度过高 (23)，考虑重构
     🔄 函数 removeUserById 的循环复杂度过高 (23)，考虑重构
     🔄 函数 removeUsersByIds 的循环复杂度过高 (23)，考虑重构
     🔄 函数 findAllUsers 的循环复杂度过高 (23)，考虑重构
     🔄 函数 findUserById 的循环复杂度过高 (23)，考虑重构
     🔄 函数 findUsersByUsername 的循环复杂度过高 (23)，考虑重构
     🔄 函数 findUsersByEmail 的循环复杂度过高 (23)，考虑重构
     🔄 函数 addUser 的循环复杂度过高 (23)，考虑重构
     🔄 函数 updateUser 的循环复杂度过高 (23)，考虑重构
     🔄 函数 deleteUserById 的循环复杂度过高 (23)，考虑重构
     🔄 函数 'BCryptPasswordEncoder' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'getCurrentUserEntity' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'convertToUserResl' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'getUsersByCondition' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'getUserById' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'getUsersByUsername' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'getAllUsers' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'getUserListForAdmin' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'validatePaginationParams' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'queryUsersForAdmin' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'buildPageResult' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'saveUser' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'preprocessUserForSave' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'updateUserById' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'preprocessUserForUpdate' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'removeUserById' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'removeUsersByIds' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'findAllUsers' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'findUserById' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'findUsersByUsername' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'findUsersByEmail' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'addUser' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'updateUser' () 复杂度严重过高 (23)，必须简化
     🔄 函数 'deleteUserById' () 复杂度严重过高 (23)，必须简化

  7. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\utils\JwtUtil.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  8. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\MyMetaObjectHandler.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  9. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\PostsService.java(屎气指数: 45.95)
     🔄 复杂度问题: 62

     🔄 函数 getPostList 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getPostList 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getPostDetail 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getPostDetail 的循环复杂度过高 (26)，考虑重构
     🔄 函数 likePost 的循环复杂度过高 (26)，考虑重构
     🔄 函数 toggleLike 的循环复杂度过高 (26)，考虑重构
     🔄 函数 toggleFavorite 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getHotPosts 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getHotPosts 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getLatestPosts 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getLatestPosts 的循环复杂度过高 (26)，考虑重构
     🔄 函数 createPost 的循环复杂度过高 (26)，考虑重构
     🔄 函数 updatePost 的循环复杂度过高 (26)，考虑重构
     🔄 函数 deletePost 的循环复杂度过高 (26)，考虑重构
     🔄 函数 publishPost 的循环复杂度过高 (26)，考虑重构
     🔄 函数 unpublishPost 的循环复杂度过高 (26)，考虑重构
     🔄 函数 updatePostStatus 的循环复杂度过高 (26)，考虑重构
     🔄 函数 savePostTags 的循环复杂度过高 (26)，考虑重构
     🔄 函数 updatePostTags 的循环复杂度过高 (26)，考虑重构
     🔄 函数 countPublishedPostsByUserId 的循环复杂度过高 (26)，考虑重构
     🔄 函数 countDraftsByUserId 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getLastPostTimeByUserId 的循环复杂度过高 (26)，考虑重构
     🔄 函数 countPostsByUserId 的循环复杂度过高 (26)，考虑重构
     🔄 函数 countAllPublishedPosts 的循环复杂度过高 (26)，考虑重构
     🔄 函数 countAllViews 的循环复杂度过高 (26)，考虑重构
     🔄 函数 countViewsByUserId 的循环复杂度过高 (26)，考虑重构
     🔄 函数 getPostListForAdmin 的循环复杂度过高 (26)，考虑重构
     🔄 函数 updatePostStatusForAdmin 的循环复杂度过高 (26)，考虑重构
     🔄 函数 deletePostForAdmin 的循环复杂度过高 (26)，考虑重构
     🔄 函数 batchUpdateStatus 的循环复杂度过高 (26)，考虑重构
     🔄 函数 removeByIds 的循环复杂度过高 (26)，考虑重构
     🔄 函数 'getPostList' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getPostList' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getPostDetail' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getPostDetail' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'likePost' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'toggleLike' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'toggleFavorite' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getHotPosts' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getHotPosts' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getLatestPosts' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getLatestPosts' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'createPost' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'updatePost' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'deletePost' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'publishPost' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'unpublishPost' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'updatePostStatus' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'savePostTags' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'updatePostTags' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'countPublishedPostsByUserId' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'countDraftsByUserId' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getLastPostTimeByUserId' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'countPostsByUserId' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'countAllPublishedPosts' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'countAllViews' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'countViewsByUserId' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'getPostListForAdmin' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'updatePostStatusForAdmin' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'deletePostForAdmin' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'batchUpdateStatus' () 复杂度严重过高 (26)，必须简化
     🔄 函数 'removeByIds' () 复杂度严重过高 (26)，必须简化

  10. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\filter\JwtAuthenticationFilter.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  11. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\UserAuthService.java(屎气指数: 45.95)
     🔄 复杂度问题: 36

     🔄 函数 BCryptPasswordEncoder 的循环复杂度过高 (20)，考虑重构
     🔄 函数 validateUserNotExists 的循环复杂度过高 (20)，考虑重构
     🔄 函数 createUserFromRegisterReq 的循环复杂度过高 (20)，考虑重构
     🔄 函数 saveUserWithExceptionHandling 的循环复杂度过高 (20)，考虑重构
     🔄 函数 handleDuplicateKeyException 的循环复杂度过高 (20)，考虑重构
     🔄 函数 convertToUserResl 的循环复杂度过高 (20)，考虑重构
     🔄 函数 login 的循环复杂度过高 (20)，考虑重构
     🔄 函数 validateUserForLogin 的循环复杂度过高 (20)，考虑重构
     🔄 函数 validatePassword 的循环复杂度过高 (20)，考虑重构
     🔄 函数 updateLastLoginTime 的循环复杂度过高 (20)，考虑重构
     🔄 函数 generateLoginResponse 的循环复杂度过高 (20)，考虑重构
     🔄 函数 changePasswordWithAuth 的循环复杂度过高 (20)，考虑重构
     🔄 函数 getCurrentUserForPasswordChange 的循环复杂度过高 (20)，考虑重构
     🔄 函数 changePassword 的循环复杂度过高 (20)，考虑重构
     🔄 函数 validateUserForPasswordChange 的循环复杂度过高 (20)，考虑重构
     🔄 函数 validateOldPassword 的循环复杂度过高 (20)，考虑重构
     🔄 函数 validateNewPassword 的循环复杂度过高 (20)，考虑重构
     🔄 函数 updateUserPassword 的循环复杂度过高 (20)，考虑重构
     🔄 函数 'BCryptPasswordEncoder' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'validateUserNotExists' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'createUserFromRegisterReq' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'saveUserWithExceptionHandling' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'handleDuplicateKeyException' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'convertToUserResl' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'login' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'validateUserForLogin' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'validatePassword' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'updateLastLoginTime' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'generateLoginResponse' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'changePasswordWithAuth' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'getCurrentUserForPasswordChange' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'changePassword' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'validateUserForPasswordChange' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'validateOldPassword' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'validateNewPassword' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'updateUserPassword' () 复杂度严重过高 (20)，必须简化

  12. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\utils\UserUtils.java(屎气指数: 45.95)
     🔄 复杂度问题: 10

     🔄 函数 getCurrentUserId 的循环复杂度较高 (15)，建议简化
     🔄 函数 getCurrentUsername 的循环复杂度较高 (15)，建议简化
     🔄 函数 getCurrentUser 的循环复杂度较高 (15)，建议简化
     🔄 函数 isCurrentUserLoggedIn 的循环复杂度较高 (15)，建议简化
     🔄 函数 isCurrentUser 的循环复杂度较高 (15)，建议简化
     🔄 函数 'getCurrentUserId' () 复杂度过高 (15)，建议简化
     🔄 函数 'getCurrentUsername' () 复杂度过高 (15)，建议简化
     🔄 函数 'getCurrentUser' () 复杂度过高 (15)，建议简化
     🔄 函数 'isCurrentUserLoggedIn' () 复杂度过高 (15)，建议简化
     🔄 函数 'isCurrentUser' () 复杂度过高 (15)，建议简化

  13. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\utils\ValidationUtil.java(屎气指数: 45.95)
     🔄 复杂度问题: 11

     🔄 函数 Pattern.compile 的循环复杂度较高 (12)，建议简化
     🔄 函数 Pattern.compile 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateNotNull 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateNotBlank 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateNotEmpty 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateId 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateEmail 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateUsername 的循环复杂度较高 (12)，建议简化
     🔄 函数 validatePassword 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateLength 的循环复杂度较高 (12)，建议简化
     🔄 函数 validateRange 的循环复杂度较高 (12)，建议简化

  14. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\AnnouncementsService.java(屎气指数: 45.95)
     🔄 复杂度问题: 26

     🔄 函数 getValidAnnouncements 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getTopAnnouncements 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getLatestAnnouncements 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getAnnouncementById 的循环复杂度过高 (32)，考虑重构
     🔄 函数 createAnnouncement 的循环复杂度过高 (32)，考虑重构
     🔄 函数 updateAnnouncement 的循环复杂度过高 (32)，考虑重构
     🔄 函数 deleteAnnouncement 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getAllAnnouncements 的循环复杂度过高 (32)，考虑重构
     🔄 函数 validateAnnouncementReq 的循环复杂度过高 (32)，考虑重构
     🔄 函数 convertToResl 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getTypeName 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getPriorityName 的循环复杂度过高 (32)，考虑重构
     🔄 函数 getStatusName 的循环复杂度过高 (32)，考虑重构
     🔄 函数 'getValidAnnouncements' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getTopAnnouncements' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getLatestAnnouncements' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getAnnouncementById' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'createAnnouncement' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'updateAnnouncement' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'deleteAnnouncement' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getAllAnnouncements' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'validateAnnouncementReq' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'convertToResl' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getTypeName' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getPriorityName' () 复杂度严重过高 (32)，必须简化
     🔄 函数 'getStatusName' () 复杂度严重过高 (32)，必须简化

  15. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\admin\PostsAdminController.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  16. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\admin\TagsAdminController.java(屎气指数: 45.95)

     ✓ 代码质量良好，没有明显问题

  17. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\admin\UsersAdminController.java(屎气指数: 45.95)
     🔄 复杂度问题: 9

     🔄 函数 LoggerFactory.getLogger 的循环复杂度较高 (11)，建议简化
     🔄 函数 getUserById 的循环复杂度较高 (11)，建议简化
     🔄 函数 createUser 的循环复杂度较高 (11)，建议简化
     🔄 函数 updateUser 的循环复杂度较高 (11)，建议简化
     🔄 函数 deleteUser 的循环复杂度较高 (11)，建议简化
     🔄 函数 batchDeleteUsers 的循环复杂度较高 (11)，建议简化
     🔄 函数 updateUserStatus 的循环复杂度较高 (11)，建议简化
     🔄 函数 preservePasswordIfEmpty 的循环复杂度较高 (11)，建议简化
     🔄 函数 buildUserStatusUpdate 的循环复杂度较高 (11)，建议简化

  18. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\PostsController.java(屎气指数: 45.95)
     🔄 复杂度问题: 28

     🔄 函数 getPostList 的循环复杂度过高 (20)，考虑重构
     🔄 函数 getPostDetail 的循环复杂度过高 (20)，考虑重构
     🔄 函数 toggleLike 的循环复杂度过高 (20)，考虑重构
     🔄 函数 toggleFavorite 的循环复杂度过高 (20)，考虑重构
     🔄 函数 getHotPosts 的循环复杂度过高 (20)，考虑重构
     🔄 函数 getLatestPosts 的循环复杂度过高 (20)，考虑重构
     🔄 函数 searchPosts 的循环复杂度过高 (20)，考虑重构
     🔄 函数 createPost 的循环复杂度过高 (20)，考虑重构
     🔄 函数 updatePost 的循环复杂度过高 (20)，考虑重构
     🔄 函数 deletePost 的循环复杂度过高 (20)，考虑重构
     🔄 函数 publishPost 的循环复杂度过高 (20)，考虑重构
     🔄 函数 unpublishPost 的循环复杂度过高 (20)，考虑重构
     🔄 函数 getDrafts 的循环复杂度过高 (20)，考虑重构
     🔄 函数 getMyPosts 的循环复杂度过高 (20)，考虑重构
     🔄 函数 'getPostList' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'getPostDetail' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'toggleLike' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'toggleFavorite' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'getHotPosts' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'getLatestPosts' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'searchPosts' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'createPost' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'updatePost' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'deletePost' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'publishPost' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'unpublishPost' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'getDrafts' () 复杂度严重过高 (20)，必须简化
     🔄 函数 'getMyPosts' () 复杂度严重过高 (20)，必须简化

  19. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\LiuTechApplication.java(屎气指数: 41.67)
     📝 注释问题: 1

     📝 代码注释率极低 (0.00%)，几乎没有注释

  20. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\CategoriesService.java(屎气指数: 39.10)

     ✓ 代码质量良好，没有明显问题

  21. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\service\TagsService.java(屎气指数: 39.10)

     ✓ 代码质量良好，没有明显问题

  22. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\LoginReq.java(屎气指数: 38.81)
     📝 注释问题: 1

     📝 代码注释率极低 (0.00%)，几乎没有注释

  23. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\RegisterReq.java(屎气指数: 38.81)
     📝 注释问题: 1

     📝 代码注释率极低 (0.00%)，几乎没有注释

  24. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\HomeController.java(屎气指数: 35.67)

     ✓ 代码质量良好，没有明显问题

  25. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\admin\BaseAdminController.java(屎气指数: 35.67)

     ✓ 代码质量良好，没有明显问题

  26. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\TagsController.java(屎气指数: 35.67)

     ✓ 代码质量良好，没有明显问题

  27. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\CommentsController.java(屎气指数: 35.67)

     ✓ 代码质量良好，没有明显问题

  28. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\utils\FileUtil.java(屎气指数: 35.67)

     ✓ 代码质量良好，没有明显问题

  29. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\common\ErrorCode.java(屎气指数: 35.67)

     ✓ 代码质量良好，没有明显问题

  30. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\FileUploadController.java(屎气指数: 32.24)

     ✓ 代码质量良好，没有明显问题

  31. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\CategoriesController.java(屎气指数: 32.24)

     ✓ 代码质量良好，没有明显问题

  32. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\common\GlobalExceptionHandler.java(屎气指数: 32.24)

     ✓ 代码质量良好，没有明显问题

  33. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\UserController.java(屎气指数: 32.24)

     ✓ 代码质量良好，没有明显问题

  34. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\SecurityConfig.java(屎气指数: 30.14)
     ⚠️  其他问题: 1

     ⚠️  函数 'filterChain' () 较长 (66 行)，可考虑重构

  35. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\MybatisPlusConfig.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  36. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\controller\web\AnnouncementsController.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  37. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\ChangePasswordReq.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  38. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\common\Result.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  39. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\TransactionConfig.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  40. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\WebConfig.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  41. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\CacheConfig.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  42. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\PageResl.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  43. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\common\BusinessException.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  44. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\config\FileUploadConfig.java(屎气指数: 28.81)

     ✓ 代码质量良好，没有明显问题

  45. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\PostFavorites.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  46. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\PostTags.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  47. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\ProfileResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  48. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\PostQueryReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  49. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\IdEntity.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  50. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\PostDetailResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  51. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\CreateCommentReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  52. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\CategoriesMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  53. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\AnnouncementReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  54. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\PostTagsMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  55. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\BaseEntity.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  56. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\UpdateProfileReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  57. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\AnnouncementResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  58. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\UserResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  59. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\TagResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  60. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\Posts.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  61. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\Comments.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  62. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\Tags.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  63. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\PostCreateResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  64. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\FileUploadResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  65. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\PostListResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  66. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\FileUploadReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  67. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\CommentResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  68. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\CategoryResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  69. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\UserStatsResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  70. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\PostLikes.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  71. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\resl\LoginResl.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  72. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\Categories.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  73. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\Users.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  74. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\model\Announcements.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  75. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\UserMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  76. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\TagsMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  77. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\PostsMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  78. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\PostUpdateReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  79. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\PostLikesMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  80. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\PostFavoritesMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  81. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\CommentsMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  82. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\req\PostCreateReq.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

  83. F:\工程代码\Liutech\LiuTech\src\main\java\chat\liuxin\liutech\mapper\AnnouncementsMapper.java(屎气指数: 25.95)

     ✓ 代码质量良好，没有明显问题

◆ 诊断结论

  🌸 偶有异味 - 基本没事，但是有伤风化

  👍 继续保持，你是编码界的一股清流，代码洁癖者的骄傲


◆ 📊 基本统计:

  📊 📊 基本统计:
    总文件数:           83
    总代码行:           10225
    总问题数:           261

  🔍 🔍 指标详细信息:

    【注释覆盖率          】(权重: 0.15)
      描述: 检测代码的注释覆盖率，良好的注释能提高代码可读性和可维护性
      得分: 3.25/100

    【状态管理           】(权重: 0.20)
      描述: 检测函数长度及状态变量管理，合理的函数长度和状态管理能提高代码可维护性
      得分: 11.87/100

    【错误处理           】(权重: 0.10)
      描述: 检测代码中的错误处理情况，良好的错误处理能提高代码的健壮性
      得分: 25.00/100

    【代码结构           】(权重: 0.15)
      描述: 检测代码的嵌套深度和引用复杂度，评估结构清晰度
      得分: 30.00/100

    【代码重复度          】(权重: 0.15)
      描述: 评估代码中重复逻辑的比例，重复代码越多，越需要抽象和重构
      得分: 35.00/100

    【循环复杂度          】(权重: 0.30)
      描述: 测量函数的控制流复杂度，复杂度越高，代码越难理解和测试
      得分: 63.49/100
