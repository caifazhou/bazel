// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.objc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.ClassObjectConstructor;
import com.google.devtools.build.lib.packages.SkylarkClassObject;
import com.google.devtools.build.lib.rules.apple.ApplePlatform;
import com.google.devtools.build.lib.rules.apple.ApplePlatform.PlatformType;
import com.google.devtools.build.lib.rules.apple.AppleToolchain;
import com.google.devtools.build.lib.rules.apple.DottedVersion;
import com.google.devtools.build.lib.rules.apple.XcodeVersionProperties;
import com.google.devtools.build.lib.rules.objc.ObjcProvider.Key;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * A class that exposes apple rule implementation internals to skylark.
 */
@SkylarkModule(
  name = "apple_common",
  doc = "Functions for skylark to access internals of the apple rule implementations."
)
public class AppleSkylarkCommon {
 
  @VisibleForTesting
  public static final String BAD_KEY_ERROR = "Argument %s not a recognized key, 'providers',"
      + " or 'direct_dep_providers'.";

  @VisibleForTesting
  public static final String BAD_SET_TYPE_ERROR =
      "Value for key %s must be a set of %s, instead found set of %s.";

  @VisibleForTesting
  public static final String BAD_PROVIDERS_ITER_ERROR =
      "Value for argument 'providers' must be a list of ObjcProvider instances, instead found %s.";

  @VisibleForTesting
  public static final String BAD_PROVIDERS_ELEM_ERROR =
      "Value for argument 'providers' must be a list of ObjcProvider instances, instead found "
          + "iterable with %s.";

  @VisibleForTesting
  public static final String NOT_SET_ERROR = "Value for key %s must be a set, instead found %s.";

  @VisibleForTesting
  public static final String MISSING_KEY_ERROR = "No value for required key %s was present.";

  @Nullable
  private SkylarkClassObject platformType;
  @Nullable
  private SkylarkClassObject platform;

  @SkylarkCallable(
      name = "apple_toolchain",
      doc = "Utilities for resolving items from the apple toolchain."
  )
  public AppleToolchain getAppleToolchain() {
    return new AppleToolchain();
  }

  @SkylarkCallable(
    name = "platform_type",
    doc = "An enum-like struct that contains the following fields corresponding to Apple platform "
        + "types:<br><ul>"
        + "<li><code>ios</code></li>"
        + "<li><code>macos</code></li>"
        + "<li><code>tvos</code></li>"
        + "<li><code>watchos</code></li>"
        + "</ul><p>"
        + "These values can be passed to methods that expect a platform type, like the 'apple' "
        + "configuration fragment's "
        + "<a href='apple.html#multi_arch_platform'>multi_arch_platform</a> method.<p>"
        + "Example:<p>"
        + "<pre class='language-python'>\n"
        + "ctx.fragments.apple.multi_arch_platform(apple_common.platform_type.ios)\n"
        + "</pre>",
    structField = true
  )
  public SkylarkClassObject getPlatformTypeStruct() {
    if (platformType == null) {
      platformType = PlatformType.getSkylarkStruct();
    }
    return platformType;
  }

  @SkylarkCallable(
      name = "platform",
      doc = "An enum-like struct that contains the following fields corresponding to Apple "
          + "platforms:<br><ul>"
          + "<li><code>ios_device</code></li>"
          + "<li><code>ios_simulator</code></li>"
          + "<li><code>macos</code></li>"
          + "<li><code>tvos_device</code></li>"
          + "<li><code>tvos_simulator</code></li>"
          + "<li><code>watchos_device</code></li>"
          + "<li><code>watchos_device</code></li>"
          + "</ul><p>"
          + "These values can be passed to methods that expect a platform, like "
          + "<a href='apple.html#sdk_version_for_platform'>apple.sdk_version_for_platform</a>.",
      structField = true
  )
  public SkylarkClassObject getPlatformStruct() {
    if (platform == null) {
      platform = ApplePlatform.getSkylarkStruct();
    }
    return platform;
  }

  @SkylarkCallable(
    name = XcodeVersionProperties.SKYLARK_NAME,
    doc = "The constructor/key for the <code>XcodeVersionProperties</code> provider.<p>"
        + "If a target propagates the <code>XcodeVersionProperties</code> provider, use this as "
        + "the key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.XcodeVersionProperties]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getXcodeVersionPropertiesConstructor() {
    return XcodeVersionProperties.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    // TODO(b/63899207): This currently does not match ObjcProvider.SKYLARK_NAME as it requires
    // a migration of existing skylark rules.
    name = "Objc",
    doc = "The constructor/key for the <code>Objc</code> provider.<p>"
        + "If a target propagates the <code>Objc</code> provider, use this as the "
        + "key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.Objc]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getObjcProviderConstructor() {
    return ObjcProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = AppleDynamicFrameworkProvider.SKYLARK_NAME,
    doc = "The constructor/key for the <code>AppleDynamicFramework</code> provider.<p>"
        + "If a target propagates the <code>AppleDynamicFramework</code> provider, use this as the "
        + "key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.AppleDynamicFramework]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getAppleDynamicFrameworkConstructor() {
    return AppleDynamicFrameworkProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = AppleDylibBinaryProvider.SKYLARK_NAME,
    doc = "The constructor/key for the <code>AppleDylibBinary</code> provider.<p>"
        + "If a target propagates the <code>AppleDylibBinary</code> provider, use this as the "
        + "key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.AppleDylibBinary]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getAppleDylibBinaryConstructor() {
    return AppleDylibBinaryProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = AppleExecutableBinaryProvider.SKYLARK_NAME,
    doc = "The constructor/key for the <code>AppleExecutableBinary</code> provider.<p>"
        + "If a target propagates the <code>AppleExecutableBinary</code> provider, use this as the "
        + "key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.AppleExecutableBinary]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getAppleExecutableBinaryConstructor() {
    return AppleExecutableBinaryProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = AppleStaticLibraryProvider.SKYLARK_NAME,
    doc = "The constructor/key for the <code>AppleStaticLibrary</code> provider.<p>"
        + "If a target propagates the <code>AppleStaticLibrary</code> provider, use this as the "
        + "key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.AppleStaticLibrary]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getAppleStaticLibraryProvider() {
    return AppleStaticLibraryProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = AppleDebugOutputsProvider.SKYLARK_NAME,
    doc = "The constructor/key for the <code>AppleDebugOutputs</code> provider.<p>"
        + "If a target propagates the <code>AppleDebugOutputs</code> provider, use this as the "
        + "key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.AppleDebugOutputs]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getAppleDebugOutputsConstructor() {
    return AppleDebugOutputsProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = AppleLoadableBundleBinaryProvider.SKYLARK_NAME,
    doc = "The constructor/key for the <code>AppleLoadableBundleBinary</code> provider.<p>"
        + "If a target propagates the <code>AppleLoadableBundleBinary</code> provider, use this as "
        + "the key with which to retrieve it. Example:<br>"
        + "<pre class='language-python'>\n"
        + "dep = ctx.attr.deps[0]\n"
        + "p = dep[apple_common.AppleLoadableBundleBinary]\n"
        + "</pre>",
    structField = true
  )
  public ClassObjectConstructor getAppleLoadableBundleBinaryConstructor() {
    return AppleLoadableBundleBinaryProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = IosDeviceProvider.SKYLARK_NAME,
    doc = "<b>Deprecated. Use the new Skylark testing rules instead.</b> Returns the provider "
        + "constructor for IosDeviceProvider. Use this as a key to access the attributes exposed "
        + "by ios_device.",
    structField = true
  )
  public ClassObjectConstructor getIosDeviceProviderConstructor() {
    return IosDeviceProvider.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkSignature(
    name = "new_objc_provider",
    objectType = AppleSkylarkCommon.class,
    returnType = ObjcProvider.class,
    doc = "Creates a new ObjcProvider instance.",
    parameters = {
      @Param(name = "self", type = AppleSkylarkCommon.class, doc = "The apple_common instance."),
      @Param(
        name = "uses_swift",
        type = Boolean.class,
        defaultValue = "False",
        named = true,
        positional = false,
        doc = "Whether this provider should enable Swift support."
      )
    },
    extraKeywords =
        @Param(
          name = "kwargs",
          type = SkylarkDict.class,
          defaultValue = "{}",
          doc = "Dictionary of arguments."
        )
  )
  public static final BuiltinFunction NEW_OBJC_PROVIDER =
      new BuiltinFunction("new_objc_provider") {
        @SuppressWarnings("unused")
        // This method is registered statically for skylark, and never called directly.
        public ObjcProvider invoke(
            AppleSkylarkCommon self, Boolean usesSwift, SkylarkDict<String, Object> kwargs) {
          ObjcProvider.Builder resultBuilder = new ObjcProvider.Builder();
          if (usesSwift) {
            resultBuilder.add(ObjcProvider.FLAG, ObjcProvider.Flag.USES_SWIFT);
          }
          for (Entry<String, Object> entry : kwargs.entrySet()) {
            Key<?> key = ObjcProvider.getSkylarkKeyForString(entry.getKey());
            if (key != null) {
              resultBuilder.addElementsFromSkylark(key, entry.getValue());
            } else if (entry.getKey().equals("providers")) {
              resultBuilder.addProvidersFromSkylark(entry.getValue());
            } else if (entry.getKey().equals("direct_dep_providers")) {
              resultBuilder.addDirectDepProvidersFromSkylark(entry.getValue());
            } else {
              throw new IllegalArgumentException(String.format(BAD_KEY_ERROR, entry.getKey()));
            }
          }
          return resultBuilder.build();
        }
      };

  @SkylarkSignature(
    name = "new_xctest_app_provider",
    objectType = AppleSkylarkCommon.class,
    returnType = XcTestAppProvider.class,
    doc = "Creates a new XcTestAppProvider instance.",
    parameters = {
      @Param(name = "self", type = AppleSkylarkCommon.class, doc = "The apple_common instance."),
      @Param(
        name = "bundle_loader",
        type = Artifact.class,
        named = true,
        positional = false,
        doc = "The bundle loader for the test. Corresponds to the binary inside the test IPA."
      ),
      @Param(
        name = "ipa",
        type = Artifact.class,
        named = true,
        positional = false,
        doc = "The test IPA."
      ),
      @Param(
        name = "objc_provider",
        type = ObjcProvider.class,
        named = true,
        positional = false,
        doc = "An ObjcProvider that should be included by tests using this test bundle."
      )
    }
  )
  public static final BuiltinFunction NEW_XCTEST_APP_PROVIDER =
      new BuiltinFunction("new_xctest_app_provider") {
        @SuppressWarnings("unused")
        // This method is registered statically for skylark, and never called directly.
        public XcTestAppProvider invoke(
            AppleSkylarkCommon self,
            Artifact bundleLoader,
            Artifact ipa,
            ObjcProvider objcProvider) {
          return new XcTestAppProvider(bundleLoader, ipa, objcProvider);
        }
      };

  @SkylarkSignature(
    name = "new_dynamic_framework_provider",
    objectType = AppleSkylarkCommon.class,
    returnType = AppleDynamicFrameworkProvider.class,
    doc = "Creates a new AppleDynamicFramework provider instance.",
    parameters = {
      @Param(name = "self", type = AppleSkylarkCommon.class, doc = "The apple_common instance."),
      @Param(
        name = AppleDynamicFrameworkProvider.DYLIB_BINARY_FIELD_NAME,
        type = Artifact.class,
        named = true,
        positional = false,
        doc = "The dylib binary artifact of the dynamic framework."
      ),
      @Param(
        name = AppleDynamicFrameworkProvider.OBJC_PROVIDER_FIELD_NAME,
        type = ObjcProvider.class,
        named = true,
        positional = false,
        doc =
            "An ObjcProvider which contains information about the transitive "
                + "dependencies linked into the binary."
      ),
      @Param(
        name = AppleDynamicFrameworkProvider.FRAMEWORK_DIRS_FIELD_NAME,
        type = SkylarkNestedSet.class,
        generic1 = String.class,
        named = true,
        noneable = true,
        positional = false,
        defaultValue = "None",
        doc =
            "The framework path names used as link inputs in order to link against the dynamic "
                + "framework."
      ),
      @Param(
        name = AppleDynamicFrameworkProvider.FRAMEWORK_FILES_FIELD_NAME,
        type = SkylarkNestedSet.class,
        generic1 = Artifact.class,
        named = true,
        noneable = true,
        positional = false,
        defaultValue = "None",
        doc =
            "The full set of artifacts that should be included as inputs to link against the "
                + "dynamic framework"
      )
    }
  )
  public static final BuiltinFunction NEW_DYNAMIC_FRAMEWORK_PROVIDER =
      new BuiltinFunction("new_dynamic_framework_provider") {
        @SuppressWarnings("unused")
        // This method is registered statically for skylark, and never called directly.
        public AppleDynamicFrameworkProvider invoke(
            AppleSkylarkCommon self,
            Artifact dylibBinary,
            ObjcProvider depsObjcProvider,
            Object dynamicFrameworkDirs,
            Object dynamicFrameworkFiles) {
          NestedSet<PathFragment> frameworkDirs;
          if (dynamicFrameworkDirs == Runtime.NONE) {
            frameworkDirs = NestedSetBuilder.<PathFragment>emptySet(Order.STABLE_ORDER);
          } else {
            Iterable<String> pathStrings =
                ((SkylarkNestedSet) dynamicFrameworkDirs).getSet(String.class);
            frameworkDirs =
                NestedSetBuilder.<PathFragment>stableOrder()
                    .addAll(Iterables.transform(pathStrings, PathFragment::create))
                    .build();
          }
          NestedSet<Artifact> frameworkFiles =
              dynamicFrameworkFiles != Runtime.NONE
                  ? ((SkylarkNestedSet) dynamicFrameworkFiles).getSet(Artifact.class)
                  : NestedSetBuilder.<Artifact>emptySet(Order.STABLE_ORDER);
          return new AppleDynamicFrameworkProvider(
              dylibBinary, depsObjcProvider, frameworkDirs, frameworkFiles);
        }
      };

  @SkylarkSignature(
    name = "dotted_version",
    objectType = AppleSkylarkCommon.class,
    returnType = DottedVersion.class,
    doc = "Creates a new <a href=\"DottedVersion.html\">DottedVersion</a> instance.",
    parameters = {
      @Param(name = "self", type = AppleSkylarkCommon.class, doc = "The apple_common instance."),
      @Param(
        name = "version",
        type = String.class,
        named = false,
        positional = false,
        doc = "The string representation of the DottedVersion."
      )
    }
  )
  public static final BuiltinFunction DOTTED_VERSION =
      new BuiltinFunction("dotted_version") {
        @SuppressWarnings("unused")
        // This method is registered statically for skylark, and never called directly.
        public DottedVersion invoke(
            AppleSkylarkCommon self, String version) {
          return DottedVersion.fromString(version);
        }
      };

  static {
    SkylarkSignatureProcessor.configureSkylarkFunctions(AppleSkylarkCommon.class);
  }
}
